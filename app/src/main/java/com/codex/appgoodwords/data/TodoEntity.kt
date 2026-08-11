package com.codex.appgoodwords.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * 오늘 해야 할 일.
 *
 * 루틴과 다릅니다. 루틴은 매일 반복하며 몇 번 했는지를 세고, 할 일은 한 번 끝내면 없어집니다.
 * 못 끝낸 할 일은 날짜에 남되 오늘 목록에 "지난 일"로 함께 보입니다([isOverdueOn]).
 */
@Entity(
    tableName = "todos",
    indices = [
        Index(value = ["syncId"], unique = true),
        Index(value = ["dueDate"])
    ]
)
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = SyncIdentity.newId(),
    val updatedAt: Long = System.currentTimeMillis(),
    val title: String,
    val note: String = "",
    /** ISO `yyyy-MM-dd`. 이 할 일이 속한 날. */
    val dueDate: String,
    /** 알람 시각(epoch millis). 없으면 알리지 않습니다. */
    val remindAt: Long? = null,
    /** 완료 시각. null이면 아직 안 한 것입니다. */
    val doneAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isDone: Boolean
        get() = doneAt != null

    /** 기준일보다 앞선 날짜인데 아직 안 끝낸 일. 오늘 목록 위쪽에 따로 모읍니다. */
    fun isOverdueOn(today: LocalDate): Boolean {
        if (isDone) return false
        val due = runCatching { LocalDate.parse(dueDate) }.getOrNull() ?: return false
        return due.isBefore(today)
    }
}
