package com.codex.appgoodwords.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "routine_memos",
    indices = [
        Index("createdAt"),
        Index("routineId", "createdAt")
    ]
)
data class RoutineMemoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val routineTitle: String,
    val body: String,
    val createdAt: Long = System.currentTimeMillis()
)
