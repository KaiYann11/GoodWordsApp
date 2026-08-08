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
    /** routineId는 기기마다 따로 증가하므로, 기기 간에는 이 값으로 루틴을 가리킨다. */
    val routineSyncId: String = "",
    val routineTitle: String,
    val checkedAt: Long = System.currentTimeMillis()
)
