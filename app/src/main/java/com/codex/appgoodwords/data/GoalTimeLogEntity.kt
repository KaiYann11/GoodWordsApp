package com.codex.appgoodwords.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 목표에 들인 시간 한 번과 그때 한 것(사용자가 정함, 2026-11-08 — "내가 이 목표를 이루기 위해 누적한 시간과 과정을
 * 기록하게").
 *
 * **되새긴 기록(덧메모, [ContentMemoEntity])과 따로 쌓습니다** — 사용자가 목업에서 "따로 '쓴 시간' 칸"을 골랐습니다.
 * 덧메모는 생각이고 이것은 실제로 들인 시간입니다. 목표 칸([ContentType.GOAL])에만 붙습니다.
 *
 * 목표는 [goalSyncId]로 가리킵니다. 숫자 id는 기기마다 따로 늘어 동기화하면 엉뚱한 목표를 가리킵니다.
 */
@Entity(
    tableName = "goal_time_logs",
    indices = [
        Index(value = ["syncId"], unique = true),
        Index(value = ["goalSyncId", "loggedAt"])
    ]
)
data class GoalTimeLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = SyncIdentity.newId(),
    val updatedAt: Long = System.currentTimeMillis(),
    val goalSyncId: String,
    /** 들인 시간(분). 1분부터 [GoalTime.MAX_MINUTES]까지입니다. */
    val minutes: Int,
    /** 그때 한 것(과정). 비어 있어도 됩니다 — 시간만 남겨도 쌓입니다. */
    val note: String = "",
    /** 그 시간을 들인 때. 직접 적으면 남긴 때, 타이머로 쟀으면 잰 것을 시작한 때입니다. */
    val loggedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)
