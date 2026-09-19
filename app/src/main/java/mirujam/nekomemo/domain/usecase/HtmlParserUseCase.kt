package mirujam.nekomemo.domain.usecase

import mirujam.nekomemo.domain.model.ExtractedQuestionBank
import mirujam.nekomemo.domain.model.QuizPlatform
import mirujam.nekomemo.domain.usecase.parser.ParserRegistry
import timber.log.Timber
import javax.inject.Inject

/**
 * 题库网页解析入口：按 [url] 的域名识别平台，分发给对应的
 * [mirujam.nekomemo.domain.usecase.parser.QuestionBankParser] 处理。
 */
class HtmlParserUseCase @Inject constructor(
    private val parserRegistry: ParserRegistry
) {

    fun parse(html: String, url: String = ""): ExtractedQuestionBank {
        val platform = QuizPlatform.fromUrl(url)
        val parser = parserRegistry.resolve(platform)
        Timber.d("HtmlParserUseCase: platform=$platform, using ${parser::class.simpleName}")
        return parser.parse(html)
    }

    fun decodeHtmlFromJs(raw: String?): String {
        if (raw == null) {
            Timber.w("decodeHtmlFromJs: input is null")
            return ""
        }

        if (raw.isBlank()) {
            Timber.w("decodeHtmlFromJs: input is blank")
            return ""
        }

        val trimmedInput = raw.trim()

        return try {
            val decoded = org.json.JSONObject("{\"v\":$trimmedInput}").getString("v")

            if (decoded.isBlank()) {
                Timber.w("decodeHtmlFromJs: decoded result is blank for input length=${trimmedInput.length}")
                return ""
            }

            Timber.d("decodeHtmlFromJs: successfully decoded ${decoded.length} chars from input ${trimmedInput.length} chars")
            decoded
        } catch (e: org.json.JSONException) {
            Timber.e(e, "decodeHtmlFromJs: JSON parsing failed for input (length=${trimmedInput.length}, preview=${trimmedInput.take(100)})")

            try {
                val fallbackDecoded = raw.replace("\\\"", "\"")
                    .replace("\\'", "'")
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\\\", "\\")

                if (fallbackDecoded != raw) {
                    Timber.d("decodeHtmlFromJs: fallback decoding succeeded, result length=${fallbackDecoded.length}")
                    fallbackDecoded
                } else {
                    Timber.w("decodeHtmlFromJs: both JSON and fallback decoding failed, returning sanitized input")
                    raw.take(10000)
                }
            } catch (e2: Exception) {
                Timber.e(e2, "decodeHtmlFromJs: fallback decoding also failed")
                raw.take(10000)
            }
        } catch (e: Exception) {
            Timber.e(e, "decodeHtmlFromJs: unexpected error for input (length=${raw.length})")
            raw.take(10000)
        }
    }
}
