package com.codex.appgoodwords.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "routine_memos",
    indices = [
        Index("createdAt"),
        Index("routineId", "createdAt"),
        Index(value = ["syncId"], unique = true)
    ]
)
data class RoutineMemoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = SyncIdentity.newId(),
    val updatedAt: Long = System.currentTimeMillis(),
    val routineId: Long,
    /** routineId는 기기마다 따로 증가하므로, 기기 간에는 이 값으로 루틴을 가리킨다. */
    val routineSyncId: String = "",
    val routineTitle: String,
    val body: String,
    val createdAt: Long = System.currentTimeMillis()
)
