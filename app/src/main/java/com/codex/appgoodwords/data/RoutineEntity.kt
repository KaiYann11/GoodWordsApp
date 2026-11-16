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
    /**
     * 이 루틴을 뽑아낸 글귀. 글귀에서 뽑지 않았으면 빈 문자열입니다.
     *
     * 숫자 id가 아니라 syncId로 가리킵니다. 숫자 id는 기기마다 따로 증가해서
     * 다른 기기로 넘어가면 엉뚱한 글귀를 가리킵니다.
     *
     * 글귀를 지워도 뽑아 둔 루틴은 남깁니다. 밟기로 한 것은 그 글귀와 별개로 이미 내 것입니다.
     */
    val sourceContentSyncId: String = "",
    val reminderEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    /**
     * 하는 요일(사용자가 정함, 2026-11-17). 일곱 칸 비트이고 기본은 매일입니다 — 요일이 생기기 전의 루틴도 매일입니다.
     * 읽고 쓰는 규칙은 [RoutineSchedule]에 있습니다. 홈의 다음 차례·위젯·진행·주기 알림은 오늘 요일의 루틴만 셉니다.
     */
    val weekdays: Int = RoutineSchedule.EVERY_DAY,
    /**
     * 정해 둔 시각(자정부터의 분). null이면 상시 — 하루 중 아무 때나 하는 루틴입니다(사용자가 정함, 2026-11-17).
     * 시각이 있으면 30분 전에 알리고, 지나도 체크가 없으면 1시간마다 세 번까지 다시 알립니다([RoutineAlarms]).
     */
    val timeOfDay: Int? = null
)
