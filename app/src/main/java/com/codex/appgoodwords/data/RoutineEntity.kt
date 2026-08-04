package com.codex.appgoodwords.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "routines",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = SyncIdentity.newId(),
    val updatedAt: Long = System.currentTimeMillis(),
    val title: String,
    val note: String = "",
    val category: String = "",
    val reminderEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
