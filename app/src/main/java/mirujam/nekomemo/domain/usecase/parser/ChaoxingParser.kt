package mirujam.nekomemo.domain.usecase.parser

import mirujam.nekomemo.domain.model.ExtractedQuestion
import mirujam.nekomemo.domain.model.ExtractedQuestionBank
import mirujam.nekomemo.domain.model.QuestionType
import mirujam.nekomemo.domain.model.QuizPlatform
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import timber.log.Timber

/**
 * 学习通（chaoxing.com）作业/考试详情页解析器。
 */
class ChaoxingParser : QuestionBankParser {

    override val platform: QuizPlatform = QuizPlatform.CHAOXING

    companion object {
        private val NUMBER_PREFIX_REGEX = Regex("^\\d+\\.\\s*")
        private val LETTER_PREFIX_REGEX = Regex("^[A-Ha-h]\\.\\s*")
        private val CORRECT_ANSWER_REGEX = Regex("正确答案[:\\s]*([A-Ha-h]+)")
        private val LETTER_REGEX = Regex("[A-Ha-h]")
    }

    override fun parse(html: String): ExtractedQuestionBank {
        Timber.d("Starting parse, HTML length: ${html.length}")
        val startTime = System.currentTimeMillis()

        val doc: Document = try {
            Jsoup.parse(html)
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse HTML")
            return ExtractedQuestionBank("", emptyList())
        }

        val bankName = doc.select("h2.mark_title").text().trim()
        Timber.d("Bank name: '$bankName'")

        val questionDivs = doc.select("div.questionLi")
        val totalQuestions = questionDivs.size
        Timber.d("Found $totalQuestions question div(s)")

        if (totalQuestions == 0) {
            Timber.w("No questions found!")
            return ExtractedQuestionBank(bankName, emptyList())
        }

        val questions = mutableListOf<ExtractedQuestion>()
        var skippedCount = 0
        var unsupportedTypeCount = 0
        var processedCount = 0

        for ((index, div) in questionDivs.withIndex()) {
            try {
                val type = parseQuestionType(div)
                if (type == null) {
                    // 无法归类到任何已支持题型（例如排序题、连线题等），跳过而不是错误猜测
                    unsupportedTypeCount++
                    continue
                }

                when (type) {
                    QuestionType.SINGLE_CHOICE, QuestionType.MULTIPLE_CHOICE, QuestionType.TRUE_FALSE -> {
                        val content = ExtractedQuestion.sanitizeContent(parseQuestionContent(div))
                        val options = parseOptions(div)
                        val correctAnswer = parseCorrectAnswer(div)
                        val correctIndices = lettersToIndices(correctAnswer)

                        if (content.isNotBlank() && options.isNotEmpty() && correctAnswer.isNotBlank()) {
                            questions.add(
                                ExtractedQuestion(
                                    type = type,
                                    content = content,
                                    options = options,
                                    correctAnswer = correctAnswer,
                                    correctIndices = correctIndices
                                )
                            )
                            processedCount++
                        } else {
                            skippedCount++
                        }
                    }
                    QuestionType.FILL_BLANK -> {
                        val content = ExtractedQuestion.sanitizeContent(parseQuestionContent(div))
                        val fillAnswers = parseFillBlankAnswers(div)
                        val correctAnswer = fillAnswers.joinToString("; ")

                        if (content.isNotBlank() && fillAnswers.isNotEmpty()) {
                            questions.add(
                                ExtractedQuestion(
                                    type = type,
                                    content = content,
                                    options = fillAnswers,
                                    correctAnswer = correctAnswer,
                                    correctIndices = fillAnswers.indices.toList()
                                )
                            )
                            processedCount++
                        } else {
                            skippedCount++
                        }
                    }
                    QuestionType.SHORT_ANSWER -> {
                        val content = ExtractedQuestion.sanitizeContent(parseQuestionContent(div))
                        val answerText = parseShortAnswerText(div)

                        if (content.isNotBlank() && answerText.isNotBlank()) {
                            questions.add(
                                ExtractedQuestion(
                                    type = type,
                                    content = content,
                                    options = listOf(answerText),
                                    correctAnswer = answerText,
                                    correctIndices = listOf(0)
                                )
                            )
                            processedCount++
                        } else {
                            skippedCount++
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w("Error parsing question $index: ${e.message}")
                skippedCount++
            }

            if (index % 50 == 0 && index > 0) {
                Timber.d("Progress: $index/$totalQuestions processed")
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        Timber.d("Parse complete in ${elapsed}ms. Valid: $processedCount/$totalQuestions, Skipped (no answer): $skippedCount, Unsupported type: $unsupportedTypeCount")

        return ExtractedQuestionBank(
            name = bankName,
            questions = questions,
            skippedCount = skippedCount,
            unsupportedTypeCount = unsupportedTypeCount
        )
    }

    /**
     * 解析题型。若存在明确的题型标识 span 但其文本不属于任何已支持题型
     * （例如排序题、连线题等学习通存在但本应用不支持的题型），返回 null 由调用方跳过，
     * 而不是像早期实现那样落到 [inferTypeFromStructure] 猜测——那样会把不支持的题型
     * 误判为单选/多选并产出错误数据。
     */
    private fun parseQuestionType(div: Element): QuestionType? {
        val typeSpan = div.select("span.colorShallow").first()
        val typeText = typeSpan?.text()?.trim() ?: return inferTypeFromStructure(div)
        return when {
            typeText.contains("单选题") -> QuestionType.SINGLE_CHOICE
            typeText.contains("多选题") -> QuestionType.MULTIPLE_CHOICE
            typeText.contains("判断题") -> QuestionType.TRUE_FALSE
            typeText.contains("填空题") -> QuestionType.FILL_BLANK
            typeText.contains("简答题") -> QuestionType.SHORT_ANSWER
            else -> null
        }
    }

    /**
     * 当 HTML 中没有明确的题型标识时，从数据结构推断题型：
     * - 有 dl.mark_fill（填空答案区）→ 填空题
     * - 正确答案有多个字母 → 多选题
     * - 正确答案只有一个字母 → 单选题
     */
    private fun inferTypeFromStructure(div: Element): QuestionType {
        // 有填空答案区 → 填空题
        if (div.select("dl.mark_fill dd.rightAnswerContent").isNotEmpty()) {
            return QuestionType.FILL_BLANK
        }
        // 从正确答案推断
        val correctAnswer = parseCorrectAnswer(div)
        if (correctAnswer.length > 1) {
            return QuestionType.MULTIPLE_CHOICE
        }
        return QuestionType.SINGLE_CHOICE
    }

    private fun parseQuestionContent(div: Element): String {
        val contentSpan = div.select("span.qtContent").first()
        if (contentSpan != null) {
            return contentSpan.text().trim()
        }

        val h3 = div.select("h3.mark_name").first() ?: return ""
        val typeSpan = h3.select("span.colorShallow").first()
        if (typeSpan != null) {
            val fullText = h3.text().trim()
            val typeText = typeSpan.text().trim()
            val contentStart = fullText.indexOf(typeText)
            if (contentStart >= 0) {
                val afterType = fullText.substring(contentStart + typeText.length).trim()
                return NUMBER_PREFIX_REGEX.replace(afterType, "").trim()
            }
        }
        return h3.text().trim()
    }

    private fun parseOptions(div: Element): List<String> {
        val optionLis = div.select("ul.mark_letter li")
        if (optionLis.isNotEmpty()) {
            return optionLis.mapNotNull { li ->
                val text = li.text().trim()
                val cleanText = LETTER_PREFIX_REGEX.replace(text, "")
                cleanText.takeIf { it.isNotBlank() }
            }
        }

        val answerDivs = div.select("div.stem_answer div.answerBg")
        if (answerDivs.isNotEmpty()) {
            return answerDivs.mapNotNull { answerDiv ->
                try {
                    val textDiv = answerDiv.select("div.answer_p").first()
                    val text = textDiv?.text()?.trim() ?: ""
                    val cleanText = LETTER_PREFIX_REGEX.replace(text, "")
                    cleanText.takeIf { it.isNotBlank() }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to parse answer div")
                    null
                }
            }
        }

        return emptyList()
    }

    private fun parseCorrectAnswer(div: Element): String {
        val correctSpan = div.select("span.rightAnswerContent").first()
        if (correctSpan != null) {
            return correctSpan.text().trim()
        }

        val greenSpan = div.select("span.colorGreen").first()
        if (greenSpan != null) {
            val text = greenSpan.text().trim()
            val match = CORRECT_ANSWER_REGEX.find(text)
            if (match != null) {
                return match.groupValues[1].uppercase()
            }
            val letterMatch = LETTER_REGEX.find(text)
            if (letterMatch != null) {
                return letterMatch.value.uppercase()
            }
        }

        return ""
    }

    private fun parseFillBlankAnswers(div: Element): List<String> {
        val rightAnswerDds = div.select("dl.mark_fill dd.rightAnswerContent")
        if (rightAnswerDds.isNotEmpty()) {
            return rightAnswerDds.mapNotNull { dd ->
                val text = dd.text().trim()
                    .replace(Regex("^\\(\\d+\\)\\s*"), "")
                text.takeIf { it.isNotBlank() }
            }
        }
        return emptyList()
    }

    /**
     * 解析简答题答案文本
     * 简答题答案可能位于 dd.rightAnswerContent（非填空题区域）、span.rightAnswerContent 或 div.answer_p 中
     */
    private fun parseShortAnswerText(div: Element): String {
        // 排除填空题区域，查找简答题答案
        val shortAnswerDds = div.select("dd.rightAnswerContent").filter { dd ->
            dd.parents().none { it.hasClass("mark_fill") }
        }
        if (shortAnswerDds.isNotEmpty()) {
            val text = shortAnswerDds.mapNotNull { dd ->
                dd.text().trim()
                    .replace(Regex("^\\(\\d+\\)\\s*"), "")
                    .takeIf { it.isNotBlank() }
            }.joinToString("; ")
            if (text.isNotBlank()) return text
        }

        // 尝试 span.rightAnswerContent（完整文本）
        val answerSpan = div.select("span.rightAnswerContent").first()
        if (answerSpan != null) {
            val text = answerSpan.text().trim()
            if (text.isNotBlank()) return text
        }

        // 尝试 div.answer_p
        val answerDivs = div.select("div.answer_p")
        if (answerDivs.isNotEmpty()) {
            val text = answerDivs.map { it.text().trim() }
                .filter { it.isNotBlank() }
                .joinToString("; ")
            if (text.isNotBlank()) return text
        }

        return ""
    }

    private fun lettersToIndices(letters: String): List<Int> {
        if (letters.isBlank()) return emptyList()
        return letters.mapNotNull { ch ->
            val index = "ABCDEFGH".indexOf(ch.uppercaseChar())
            if (index >= 0) index else null
        }
    }
}
