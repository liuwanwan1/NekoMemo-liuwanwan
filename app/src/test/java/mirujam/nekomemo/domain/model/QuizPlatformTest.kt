package mirujam.nekomemo.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class QuizPlatformTest {

    @Test
    fun fromUrl_detectsKnownPlatformsByHost() {
        assertEquals(QuizPlatform.CHAOXING, QuizPlatform.fromUrl("https://i.chaoxing.com/exam/test/reVideo"))
        assertEquals(QuizPlatform.CHAOXING, QuizPlatform.fromUrl("https://passport2.chaoxing.com/login"))
        assertEquals(QuizPlatform.ZHIHUISHU, QuizPlatform.fromUrl("https://www.zhihuishu.com/course/123"))
        assertEquals(QuizPlatform.ICOURSE163, QuizPlatform.fromUrl("https://www.icourse163.org/learn/abc"))
    }

    @Test
    fun fromUrl_returnsOtherForUnknownOrInvalidUrl() {
        assertEquals(QuizPlatform.OTHER, QuizPlatform.fromUrl("https://example.edu.cn/course"))
        assertEquals(QuizPlatform.OTHER, QuizPlatform.fromUrl(""))
        assertEquals(QuizPlatform.OTHER, QuizPlatform.fromUrl("not a url"))
    }
}
