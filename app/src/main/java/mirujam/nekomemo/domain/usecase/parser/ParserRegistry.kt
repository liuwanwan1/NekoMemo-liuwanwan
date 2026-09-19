package mirujam.nekomemo.domain.usecase.parser

import mirujam.nekomemo.domain.model.QuizPlatform
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 按平台分发到对应的 [QuestionBankParser]。新增平台支持时，实现一个新的
 * [QuestionBankParser] 并在这里注册即可，调用方（HtmlParserUseCase）不需要改动。
 */
@Singleton
class ParserRegistry @Inject constructor() {

    private val chaoxingParser: QuestionBankParser = ChaoxingParser()
    private val genericParser: QuestionBankParser = GenericHeuristicParser()

    fun resolve(platform: QuizPlatform): QuestionBankParser = when (platform) {
        QuizPlatform.CHAOXING -> chaoxingParser
        QuizPlatform.ZHIHUISHU, QuizPlatform.ICOURSE163, QuizPlatform.OTHER -> genericParser
    }
}
