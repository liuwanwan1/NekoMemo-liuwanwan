package mirujam.nekomemo.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class PracticeSession(
    val id: Long = 0,
    val questionBankId: Long?,
    val bankTitleSnapshot: String,
    val startedAt: Long,
    val finishedAt: Long,
    val totalCount: Int,
    val correctCount: Int
) {
    val wrongCount: Int get() = totalCount - correctCount
    val accuracyPercent: Int get() = if (totalCount > 0) (correctCount * 100) / totalCount else 0
}

@Immutable
data class OverallPracticeStats(
    val sessionCount: Int,
    val totalAnswered: Int,
    val totalCorrect: Int,
    val wrongQuestionCount: Int,
    val masteredQuestionCount: Int
) {
    val accuracyPercent: Int get() = if (totalAnswered > 0) (totalCorrect * 100) / totalAnswered else 0

    companion object {
        val EMPTY = OverallPracticeStats(0, 0, 0, 0, 0)
    }
}
