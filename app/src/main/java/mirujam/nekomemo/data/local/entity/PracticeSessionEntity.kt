package mirujam.nekomemo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一次练习/测试的历史记录。questionBankId 为 null 表示跨题库的错题复习。
 * 题库被删除时 questionBankId 置空（ON DELETE SET NULL），历史记录本身保留。
 */
@Entity(
    tableName = "practice_sessions",
    foreignKeys = [
        ForeignKey(
            entity = QuestionBankEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionBankId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("questionBankId"), Index("startedAt")]
)
data class PracticeSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val questionBankId: Long?,
    val bankTitleSnapshot: String,
    val startedAt: Long,
    val finishedAt: Long,
    val totalCount: Int,
    val correctCount: Int
)
