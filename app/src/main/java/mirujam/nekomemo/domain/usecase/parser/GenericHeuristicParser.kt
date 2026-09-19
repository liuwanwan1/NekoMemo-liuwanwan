package mirujam.nekomemo.domain.usecase.parser

import mirujam.nekomemo.domain.model.ExtractedQuestion
import mirujam.nekomemo.domain.model.ExtractedQuestionBank
import mirujam.nekomemo.domain.model.QuestionType
import mirujam.nekomemo.domain.model.QuizPlatform
import org.jsoup.Jsoup
import timber.log.Timber

/**
 * 智慧树、中国大学 MOOC 等平台目前没有专门适配的解析器，其"查看答案"页面需要登录后才能
 * 拿到真实 HTML 结构，本项目无法在开发环境中获取样本来编写针对性的 CSS selector。
 *
 * 这里改用不依赖具体 DOM class 名的通用兜底策略：直接在渲染后的纯文本上按题型关键字
 * （单选题/多选题/判断题/填空题/简答题）切分题块，再用选项前缀（A./B./…）和
 * "正确答案/参考答案"关键字识别选项与答案。只要目标页面的文字内容遵循这类常见的
 * 中文试题排版习惯，就有较大概率被正确提取；反之则该题会被计入 skippedCount，
 * 不会产出编造的错误数据。使用前建议先在"查看 HTML"里核对提取结果。
 */
class GenericHeuristicParser : QuestionBankParser {

    override val platform: QuizPlatform = QuizPlatform.OTHER

    companion object {
        private val TYPE_LABEL_REGEX = Regex("^[(（]?\\d*[)）.、]?\\s*(单选题|多选题|判断题|填空题|简答题)\\s*[)）]?\\s*(\\(\\s*\\d+(\\.\\d+)?\\s*分\\s*\\))?")
        private val OPTION_LINE_REGEX = Regex("^[(（]?([A-Ha-h])[)）.、]\\s*(.+)$")
        private val ANSWER_LINE_REGEX = Regex("(正确答案|参考答案|答案)[:：]?\\s*(.*)$")
        private val ANSWER_LETTERS_ONLY_REGEX = Regex("^[A-Ha-h、,，\\s]+$")
        private val BLANK_INDEX_PREFIX_REGEX = Regex("^[(（]\\d+[)）]\\s*")

        private fun typeFromLabel(label: String): QuestionType = when (label) {
            "单选题" -> QuestionType.SINGLE_CHOICE
            "多选题" -> QuestionType.MULTIPLE_CHOICE
            "判断题" -> QuestionType.TRUE_FALSE
            "填空题" -> QuestionType.FILL_BLANK
            "简答题" -> QuestionType.SHORT_ANSWER
            else -> QuestionType.SINGLE_CHOICE
        }
    }

    override fun parse(html: String): ExtractedQuestionBank {
        val startTime = System.currentTimeMillis()

        val doc = try {
            Jsoup.parse(html)
        } catch (e: Exception) {
            Timber.e(e, "GenericHeuristicParser: failed to parse HTML")
            return ExtractedQuestionBank("", emptyList())
        }

        val bodyText = doc.body()?.wholeText() ?: ""
        val lines = bodyText.lines().map { it.trim() }.filter { it.isNotBlank() }

        val startIndices = lines.indices.filter { TYPE_LABEL_REGEX.containsMatchIn(lines[it]) }
        if (startIndices.isEmpty()) {
            Timber.w("GenericHeuristicParser: no recognizable question blocks found")
            return ExtractedQuestionBank("", emptyList())
        }

        val questions = mutableListOf<ExtractedQuestion>()
        var skippedCount = 0

        for ((blockIndex, startIndex) in startIndices.withIndex()) {
            val endIndex = startIndices.getOrElse(blockIndex + 1) { lines.size }
            val blockLines = lines.subList(startIndex, endIndex)
            try {
                val question = parseBlock(blockLines)
                if (question != null) {
                    questions.add(question)
                } else {
                    skippedCount++
                }
            } catch (e: Exception) {
                Timber.w(e, "GenericHeuristicParser: failed to parse block at line $startIndex")
                skippedCount++
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        Timber.d("GenericHeuristicParser: parsed in ${elapsed}ms, valid=${questions.size}, skipped=$skippedCount")

        return ExtractedQuestionBank(
            name = "",
            questions = questions,
            skippedCount = skippedCount,
            unsupportedTypeCount = 0
        )
    }

    private fun parseBlock(blockLines: List<String>): ExtractedQuestion? {
        val labelMatch = TYPE_LABEL_REGEX.find(blockLines.first()) ?: return null
        val type = typeFromLabel(labelMatch.groupValues[1])

        val remainderOfFirstLine = blockLines.first().substring(labelMatch.range.last + 1).trim()
        val optionLines = mutableListOf<Pair<Char, String>>()
        val stemLines = mutableListOf<String>()
        var answerRaw: String? = null
        if (remainderOfFirstLine.isNotBlank()) stemLines.add(remainderOfFirstLine)

        var i = 1
        while (i < blockLines.size) {
            val line = blockLines[i]
            val optionMatch = OPTION_LINE_REGEX.find(line)
            val answerMatch = ANSWER_LINE_REGEX.find(line)
            when {
                answerMatch != null -> {
                    answerRaw = answerMatch.groupValues[2].trim()
                }
                optionMatch != null && answerRaw == null -> {
                    optionLines.add(optionMatch.groupValues[1][0].uppercaseChar() to optionMatch.groupValues[2].trim())
                }
                answerRaw == null -> {
                    stemLines.add(line)
                }
            }
            i++
        }

        val content = ExtractedQuestion.sanitizeContent(stemLines.joinToString(" ").trim())
        if (content.isBlank()) return null

        return when (type) {
            QuestionType.SINGLE_CHOICE, QuestionType.MULTIPLE_CHOICE, QuestionType.TRUE_FALSE -> {
                val options = optionLines.map { it.second }
                val answer = answerRaw?.trim().orEmpty()
                if (options.isEmpty() || answer.isBlank()) return null
                val correctLetters = if (ANSWER_LETTERS_ONLY_REGEX.matches(answer)) {
                    answer.filter { it.isLetter() }.uppercase()
                } else {
                    // 答案不是纯字母（可能是选项原文），按内容匹配选项
                    optionLines.filter { answer.contains(it.second) }
                        .map { it.first }
                        .joinToString("")
                }
                if (correctLetters.isBlank()) return null
                val correctIndices = correctLetters.mapNotNull { ch -> "ABCDEFGH".indexOf(ch).takeIf { it >= 0 } }
                if (correctIndices.isEmpty()) return null
                ExtractedQuestion(
                    type = type,
                    content = content,
                    options = options,
                    correctAnswer = correctLetters,
                    correctIndices = correctIndices
                )
            }
            QuestionType.FILL_BLANK, QuestionType.SHORT_ANSWER -> {
                val answer = answerRaw?.trim().orEmpty()
                if (answer.isBlank()) return null
                val answers = answer.split(Regex("[;；]"))
                    .map { BLANK_INDEX_PREFIX_REGEX.replace(it.trim(), "") }
                    .filter { it.isNotBlank() }
                    .ifEmpty { listOf(answer) }
                ExtractedQuestion(
                    type = type,
                    content = content,
                    options = answers,
                    correctAnswer = answers.joinToString("; "),
                    correctIndices = answers.indices.toList()
                )
            }
        }
    }
}
