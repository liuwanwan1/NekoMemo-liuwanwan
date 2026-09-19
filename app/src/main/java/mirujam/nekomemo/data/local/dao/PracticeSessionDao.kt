package mirujam.nekomemo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import mirujam.nekomemo.data.local.entity.PracticeSessionEntity

@Dao
interface PracticeSessionDao {

    @Insert
    suspend fun insert(session: PracticeSessionEntity): Long

    @Query("SELECT * FROM practice_sessions ORDER BY startedAt DESC")
    fun getAllSessions(): Flow<List<PracticeSessionEntity>>

    @Query("SELECT * FROM practice_sessions ORDER BY startedAt DESC LIMIT :limit")
    fun getRecentSessions(limit: Int): Flow<List<PracticeSessionEntity>>

    @Query("SELECT COUNT(*) FROM practice_sessions")
    fun getSessionCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(totalCount), 0) FROM practice_sessions")
    fun getTotalAnsweredCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(correctCount), 0) FROM practice_sessions")
    fun getTotalCorrectCount(): Flow<Int>

    @Query("DELETE FROM practice_sessions")
    suspend fun deleteAll()
}
