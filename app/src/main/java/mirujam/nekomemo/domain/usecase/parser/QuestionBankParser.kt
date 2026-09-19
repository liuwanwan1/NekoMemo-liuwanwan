package mirujam.nekomemo.domain.usecase.parser

import mirujam.nekomemo.domain.model.ExtractedQuestionBank
import mirujam.nekomemo.domain.model.QuizPlatform

/**
 * 单一平台的题库网页解析策略。新增平台支持只需实现该接口并注册到 [ParserRegistry]，
 * 不需要改动调用方（[mirujam.nekomemo.domain.usecase.HtmlParserUseCase]）。
 */
interface QuestionBankParser {
    val platform: QuizPlatform
    fun parse(html: String): ExtractedQuestionBank
}
