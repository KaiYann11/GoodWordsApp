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
    /**
     * 하루에 밟는 차례. 작을수록 먼저입니다.
     *
     * 루틴은 할 일과 달리 순서가 있습니다(일어나서 물 → 스트레칭 → 책 한 쪽).
     * 만든 시각으로는 그 차례를 나타낼 수 없어 따로 둡니다.
     * 기기마다 같은 차례를 보여야 해서 동기화 대상입니다.
     */
    val orderIndex: Int = 0,
    val reminderEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
