package mirujam.nekomemo.domain.usecase.parser

import mirujam.nekomemo.domain.model.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GenericHeuristicParser 是智慧树/中国大学 MOOC 等平台的兜底解析器，不依赖具体 DOM
 * class 名，而是在纯文本上按题型关键字/选项前缀/答案关键字识别。这里用模拟的
 * 纯文本试题排版（而非真实平台样本，登录后页面无法在本环境获取）验证其识别逻辑。
 */
class GenericHeuristicParserTest {

    private val parser = GenericHeuristicParser()

    @Test
    fun parse_extractsAllFiveQuestionTypesFromPlainTextLayout() {
        val html = """
            <html><body>
            1.单选题 (2分) 中国的首都是？
            A.北京
            B.上海
            C.广州
            D.深圳
            正确答案：A

            2.多选题 下列属于水果的是？
            A.苹果
            B.土豆
            C.香蕉
            D.白菜
            正确答案：AC

            3.判断题 地球是圆的。
            A.正确
            B.错误
            正确答案：A

            4.填空题 1+1=___
            参考答案：2

            5.简答题 请简述光合作用。
            参考答案：植物利用光能将二氧化碳和水转化为有机物的过程
            </body></html>
        """.trimIndent()

        val result = parser.parse(html)

        assertEquals(5, result.questions.size)
        assertEquals(0, result.skippedCount)

        val single = result.questions[0]
        assertEquals(QuestionType.SINGLE_CHOICE, single.type)
        assertEquals(listOf(0), single.correctIndices)
        assertEquals(listOf("北京", "上海", "广州", "深圳"), single.options)

        val multi = result.questions[1]
        assertEquals(QuestionType.MULTIPLE_CHOICE, multi.type)
        assertEquals(listOf(0, 2), multi.correctIndices)

        val trueFalse = result.questions[2]
        assertEquals(QuestionType.TRUE_FALSE, trueFalse.type)
        assertEquals(listOf(0), trueFalse.correctIndices)

        val fillBlank = result.questions[3]
        assertEquals(QuestionType.FILL_BLANK, fillBlank.type)
        assertEquals(listOf("2"), fillBlank.options)

        val shortAnswer = result.questions[4]
        assertEquals(QuestionType.SHORT_ANSWER, shortAnswer.type)
        assertEquals("植物利用光能将二氧化碳和水转化为有机物的过程", shortAnswer.correctAnswer)
    }

    @Test
    fun parse_returnsEmptyResultWhenTextDoesNotMatchAnyKnownPattern() {
        val result = parser.parse("<html><body>这是一段完全无法识别的普通文本，不包含任何题型关键字。</body></html>")

        assertTrue(result.questions.isEmpty())
    }

    @Test
    fun parse_skipsBlockMissingOptionsOrAnswer() {
        val html = """
            <html><body>
            1.单选题 这道题缺少选项和答案
            </body></html>
        """.trimIndent()

        val result = parser.parse(html)

        assertTrue(result.questions.isEmpty())
        assertEquals(1, result.skippedCount)
    }
}
