package mirujam.nekomemo.domain.model

import androidx.annotation.StringRes
import mirujam.nekomemo.R

/**
 * 支持抓取题目的刷题平台。用于 Fetcher 页快速导航和解析器分发。
 */
enum class QuizPlatform(@StringRes val displayNameRes: Int, val homeUrl: String) {
    CHAOXING(R.string.platform_chaoxing, "https://i.chaoxing.com"),
    ZHIHUISHU(R.string.platform_zhihuishu, "https://www.zhihuishu.com"),
    ICOURSE163(R.string.platform_icourse163, "https://www.icourse163.org"),
    OTHER(R.string.platform_other, "https://i.chaoxing.com");

    companion object {
        /** 根据 URL 的 host 识别所属平台，未知 host 归为 OTHER，走通用解析兜底。 */
        fun fromUrl(url: String): QuizPlatform {
            val host = try {
                java.net.URI(url).host?.lowercase()
            } catch (e: Exception) {
                null
            } ?: return OTHER
            return when {
                host.endsWith("chaoxing.com") -> CHAOXING
                host.endsWith("zhihuishu.com") -> ZHIHUISHU
                host.endsWith("icourse163.org") -> ICOURSE163
                else -> OTHER
            }
        }
    }
}
