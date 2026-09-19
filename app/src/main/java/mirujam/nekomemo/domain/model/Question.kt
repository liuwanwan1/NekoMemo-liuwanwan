package mirujam.nekomemo.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Question(
    val id: Long = 0,
    val questionBankId: Long,
    val text: String,
    val options: List<String>,
    val correctIndices: List<Int>,
    val type: QuestionType,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val consecutiveCorrect: Int = 0,
    val lastAnsweredAt: Long? = null
) {
    /** 连续答对次数达到阈值即视为已掌握，答错会重置为 0（见 [wrongCount]）。 */
    val isMastered: Boolean get() = consecutiveCorrect >= MASTERY_THRESHOLD

    companion object {
        const val MASTERY_THRESHOLD = 3
    }
}
