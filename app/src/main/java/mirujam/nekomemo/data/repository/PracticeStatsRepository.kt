package mirujam.nekomemo.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import mirujam.nekomemo.data.local.dao.PracticeSessionDao
import mirujam.nekomemo.data.local.entity.PracticeSessionEntity
import mirujam.nekomemo.data.mapper.toDomainSessionModels
import mirujam.nekomemo.domain.model.OverallPracticeStats
import mirujam.nekomemo.domain.model.PracticeSession
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PracticeStatsRepository @Inject constructor(
    private val practiceSessionDao: PracticeSessionDao,
    private val questionRepository: QuestionRepository
) {

    fun getAllSessions(): Flow<List<PracticeSession>> =
        practiceSessionDao.getAllSessions().map { it.toDomainSessionModels() }

    fun getRecentSessions(limit: Int): Flow<List<PracticeSession>> =
        practiceSessionDao.getRecentSessions(limit).map { it.toDomainSessionModels() }

    fun getOverallStats(): Flow<OverallPracticeStats> =
        combine(
            practiceSessionDao.getSessionCount(),
            practiceSessionDao.getTotalAnsweredCount(),
            practiceSessionDao.getTotalCorrectCount(),
            questionRepository.getWrongQuestionCount(),
            questionRepository.getMasteredQuestionCount()
        ) { sessionCount, totalAnswered, totalCorrect, wrongCount, masteredCount ->
            OverallPracticeStats(
                sessionCount = sessionCount,
                totalAnswered = totalAnswered,
                totalCorrect = totalCorrect,
                wrongQuestionCount = wrongCount,
                masteredQuestionCount = masteredCount
            )
        }

    /**
     * 记录一次练习会话及每道题的作答结果。objectiveResults 只包含客观题
     * （单选/多选/判断）的作答对错，填空题/简答题为自评不计入错题本统计，
     * 但仍计入本次会话的 totalCount/correctCount。
     */
    suspend fun recordSession(
        questionBankId: Long?,
        bankTitleSnapshot: String,
        startedAt: Long,
        finishedAt: Long,
        totalCount: Int,
        correctCount: Int,
        objectiveResults: List<Pair<Long, Boolean>>
    ) {
        questionRepository.recordAnswers(objectiveResults, finishedAt)
        practiceSessionDao.insert(
            PracticeSessionEntity(
                questionBankId = questionBankId,
                bankTitleSnapshot = bankTitleSnapshot,
                startedAt = startedAt,
                finishedAt = finishedAt,
                totalCount = totalCount,
                correctCount = correctCount
            )
        )
    }
}
