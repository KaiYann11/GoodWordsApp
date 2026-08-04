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
    val routineTitle: String,
    val body: String,
    val createdAt: Long = System.currentTimeMillis()
)
