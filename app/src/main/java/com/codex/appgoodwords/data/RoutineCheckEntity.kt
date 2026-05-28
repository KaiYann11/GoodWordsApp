package com.codex.appgoodwords.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "routine_checks",
    indices = [
        Index("checkedAt"),
        Index("routineId", "checkedAt")
    ]
)
data class RoutineCheckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val routineTitle: String,
    val checkedAt: Long = System.currentTimeMillis()
)
