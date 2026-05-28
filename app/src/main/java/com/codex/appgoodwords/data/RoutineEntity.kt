package com.codex.appgoodwords.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    val category: String = "",
    val reminderEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
