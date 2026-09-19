package mirujam.nekomemo.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mirujam.nekomemo.data.local.NekoMemoDatabase
import mirujam.nekomemo.data.local.dao.QuestionBankDao
import mirujam.nekomemo.data.local.dao.QuestionDao
import mirujam.nekomemo.data.local.entity.QuestionCountByBank
import mirujam.nekomemo.data.mapper.toDomainBankModels
import mirujam.nekomemo.data.mapper.toDomainModel
import mirujam.nekomemo.data.mapper.toDomainQuestionModels
import mirujam.nekomemo.data.mapper.toEntity
import mirujam.nekomemo.domain.model.Question
import mirujam.nekomemo.domain.model.QuestionBank
import mirujam.nekomemo.domain.model.QuestionType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuestionRepository @Inject constructor(
    private val questionBankDao: QuestionBankDao,
    private val questionDao: QuestionDao,
    private val database: NekoMemoDatabase
) {

    fun getAllBanks(): Flow<List<QuestionBank>> =
        questionBankDao.getAllBanks().map { it.toDomainBankModels() }

    suspend fun getBankById(id: Long): QuestionBank? =
        questionBankDao.getBankById(id)?.toDomainModel()

    fun getBankByIdFlow(id: Long): Flow<QuestionBank?> =
        questionBankDao.getBankByIdFlow(id).map { it?.toDomainModel() }

    suspend fun insertBank(bank: QuestionBank): Long =
        questionBankDao.insertBank(bank.toEntity())

    suspend fun updateBank(bank: QuestionBank) =
        questionBankDao.updateBank(bank.toEntity())

    suspend fun deleteBank(bank: QuestionBank) =
        questionBankDao.deleteBank(bank.toEntity())

    fun getBankCount(): Flow<Int> =
        questionBankDao.getBankCount()

    fun getTotalQuestionCount(): Flow<Int> =
        questionDao.getTotalQuestionCount()

    fun getQuestionCountsByBank(): Flow<List<QuestionCountByBank>> =
        questionDao.getQuestionCountsByBank()

    fun getQuestionCountForBank(bankId: Long): Flow<Int> =
        questionDao.getQuestionCountForBank(bankId)

    fun getQuestionsForBank(bankId: Long): Flow<List<Question>> =
        questionDao.getQuestionsForBank(bankId).map { it.toDomainQuestionModels() }

    /** 统一查询：query 为空时返回所有题目，非空时按文本搜索 */
    fun queryQuestionsForBank(bankId: Long, query: String): Flow<List<Question>> =
        questionDao.queryQuestionsForBank(bankId, query).map { it.toDomainQuestionModels() }

    suspend fun getQuestionsForBankSync(bankId: Long): List<Question> =
        questionDao.getQuestionsForBankSync(bankId).toDomainQuestionModels()

    suspend fun getQuestionById(id: Long): Question? =
        questionDao.getQuestionById(id)?.toDomainModel()

    suspend fun insertQuestions(questions: List<Question>) {
        if (questions.isNotEmpty()) {
            questionDao.insertAll(questions.map { it.toEntity() })
        }
    }

    suspend fun createBankWithQuestions(bank: QuestionBank, questions: List<Question>): Long =
        database.withTransaction {
            val bankId = questionBankDao.insertBank(bank.toEntity())
            if (questions.isNotEmpty()) {
                questionDao.insertAll(questions.map { it.copy(questionBankId = bankId).toEntity() })
            }
            bankId
        }

    suspend fun updateQuestion(id: Long, questionBankId: Long, text: String, options: List<String>, correctIndices: List<Int>, type: QuestionType) {
        // 保留已有的答题统计（correctCount/wrongCount/consecutiveCorrect/lastAnsweredAt），
        // 仅编辑题目内容，避免编辑题目时把错题本/掌握度记录清零
        val existing = questionDao.getQuestionById(id)
        val entity = Question(id, questionBankId, text, options, correctIndices, type).toEntity().let { updated ->
            if (existing != null) {
                updated.copy(
                    correctCount = existing.correctCount,
                    wrongCount = existing.wrongCount,
                    consecutiveCorrect = existing.consecutiveCorrect,
                    lastAnsweredAt = existing.lastAnsweredAt
                )
            } else {
                updated
            }
        }
        questionDao.updateQuestion(entity)
    }

    suspend fun deleteQuestion(question: Question) =
        questionDao.deleteQuestion(question.toEntity())

    /** 错题本：所有答错次数 > 0 的题目（跨题库） */
    fun getWrongQuestions(): Flow<List<Question>> =
        questionDao.getWrongQuestions().map { it.toDomainQuestionModels() }

    fun getWrongQuestionsForBank(bankId: Long): Flow<List<Question>> =
        questionDao.getWrongQuestionsForBank(bankId).map { it.toDomainQuestionModels() }

    fun getWrongQuestionCount(): Flow<Int> = questionDao.getWrongQuestionCount()

    fun getMasteredQuestionCount(): Flow<Int> = questionDao.getMasteredQuestionCount(Question.MASTERY_THRESHOLD)

    /** 手动标记为已掌握 / 移出错题本 */
    suspend fun markQuestionAsMastered(id: Long) = questionDao.markAsMastered(id, Question.MASTERY_THRESHOLD)

    /** 记录一批题目的答题结果（正确/错误），用于测试完成后更新错题本与掌握度统计 */
    suspend fun recordAnswers(results: List<Pair<Long, Boolean>>, answeredAt: Long = System.currentTimeMillis()) {
        if (results.isEmpty()) return
        database.withTransaction {
            results.forEach { (questionId, isCorrect) ->
                if (isCorrect) {
                    questionDao.recordCorrectAnswer(questionId, answeredAt)
                } else {
                    questionDao.recordWrongAnswer(questionId, answeredAt)
                }
            }
        }
    }

    suspend fun deleteAllData() = database.withTransaction {
        questionDao.deleteAll()
        questionBankDao.deleteAll()
    }

    suspend fun duplicateBank(bankId: Long): Long {
        return database.withTransaction {
            val originalBank = questionBankDao.getBankById(bankId) ?: return@withTransaction -1L
            val newBankId = questionBankDao.insertBank(
                originalBank.copy(
                    id = 0,
                    title = "${originalBank.title} (Copy)",
                    createdAt = System.currentTimeMillis()
                )
            )
            val questions = questionDao.getQuestionsForBankSync(bankId)
            if (questions.isNotEmpty()) {
                // 复制出的题库是全新的练习记录，不继承原题库的错题/掌握度统计
                questionDao.insertAll(
                    questions.map {
                        it.copy(
                            id = 0,
                            questionBankId = newBankId,
                            correctCount = 0,
                            wrongCount = 0,
                            consecutiveCorrect = 0,
                            lastAnsweredAt = null
                        )
                    }
                )
            }
            newBankId
        }
    }
}
