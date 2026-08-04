package com.codex.appgoodwords.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "routine_checks",
    indices = [
        Index("checkedAt"),
        Index("routineId", "checkedAt"),
        Index(value = ["syncId"], unique = true)
    ]
)
data class RoutineCheckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // 체크는 한 번 기록되면 바뀌지 않으므로 updatedAt 없이 식별자만 둔다.
    val syncId: String = SyncIdentity.newId(),
    val routineId: Long,
    val routineTitle: String,
    val checkedAt: Long = System.currentTimeMillis()
)
