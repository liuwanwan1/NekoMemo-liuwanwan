package mirujam.nekomemo.domain.usecase.parser

import mirujam.nekomemo.domain.model.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChaoxingParserTest {

    private val parser = ChaoxingParser()

    @Test
    fun parse_extractsBankNameAndAllSupportedQuestionTypes() {
        val html = """
            <html><body>
            <h2 class="mark_title">测试题库</h2>
            <div class="questionLi">
                <h3 class="mark_name">1.<span class="colorShallow">单选题</span>下列哪个是正确答案？</h3>
                <div class="stem_answer">
                    <ul class="mark_letter">
                        <li>A.选项一</li>
                        <li>B.选项二</li>
                        <li>C.选项三</li>
                        <li>D.选项四</li>
                    </ul>
                </div>
                <span class="rightAnswerContent">B</span>
            </div>
            <div class="questionLi">
                <h3 class="mark_name">2.<span class="colorShallow">多选题</span>下列哪些是水果？</h3>
                <div class="stem_answer">
                    <ul class="mark_letter">
                        <li>A.苹果</li>
                        <li>B.土豆</li>
                        <li>C.香蕉</li>
                        <li>D.白菜</li>
                    </ul>
                </div>
                <span class="rightAnswerContent">AC</span>
            </div>
            <div class="questionLi">
                <h3 class="mark_name">3.<span class="colorShallow">填空题</span>1+1=___</h3>
                <dl class="mark_fill">
                    <dd class="rightAnswerContent">(1)2</dd>
                </dl>
            </div>
            <div class="questionLi">
                <h3 class="mark_name">4.<span class="colorShallow">简答题</span>请简述光合作用</h3>
                <div class="answer_p">植物利用光能将二氧化碳和水转化为有机物的过程</div>
            </div>
            </body></html>
        """.trimIndent()

        val result = parser.parse(html)

        assertEquals("测试题库", result.name)
        assertEquals(4, result.questions.size)
        assertEquals(0, result.skippedCount)
        assertEquals(0, result.unsupportedTypeCount)

        val single = result.questions[0]
        assertEquals(QuestionType.SINGLE_CHOICE, single.type)
        assertEquals(listOf("选项一", "选项二", "选项三", "选项四"), single.options)
        assertEquals(listOf(1), single.correctIndices)

        val multi = result.questions[1]
        assertEquals(QuestionType.MULTIPLE_CHOICE, multi.type)
        assertEquals(listOf(0, 2), multi.correctIndices)

        val fillBlank = result.questions[2]
        assertEquals(QuestionType.FILL_BLANK, fillBlank.type)
        assertEquals(listOf("2"), fillBlank.options)

        val shortAnswer = result.questions[3]
        assertEquals(QuestionType.SHORT_ANSWER, shortAnswer.type)
        assertEquals("植物利用光能将二氧化碳和水转化为有机物的过程", shortAnswer.correctAnswer)
    }

    @Test
    fun parse_unsupportedQuestionTypeIsSkippedNotMisclassified() {
        // 排序题不在支持的 5 种题型内。旧实现会 fallback 到 inferTypeFromStructure，
        // 依据正确答案字母数把它错误地当成单选/多选题产出。修复后应跳过并计入 unsupportedTypeCount。
        val html = """
            <html><body>
            <h2 class="mark_title">题库</h2>
            <div class="questionLi">
                <h3 class="mark_name">1.<span class="colorShallow">排序题</span>请对下列步骤排序</h3>
                <span class="rightAnswerContent">BAC</span>
            </div>
            </body></html>
        """.trimIndent()

        val result = parser.parse(html)

        assertEquals(0, result.questions.size)
        assertEquals(1, result.unsupportedTypeCount)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun parse_returnsEmptyBankWhenNoQuestionsFound() {
        val result = parser.parse("<html><body><p>没有题目</p></body></html>")

        assertTrue(result.questions.isEmpty())
    }
}
