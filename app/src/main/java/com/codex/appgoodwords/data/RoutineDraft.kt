package com.codex.appgoodwords.data

data class RoutineDraft(
    val id: Long = 0,
    val title: String = "",
    val note: String = "",
    val category: String = "",
    /** 이 루틴을 뽑아낸 글귀. 직접 만든 루틴이면 빈 문자열입니다. */
    val sourceContentSyncId: String = "",
    val reminderEnabled: Boolean = true,
    /** 하는 요일([RoutineSchedule]). 고치기 창이 늘 들고 다닙니다 — 빠지면 저장할 때 매일로 돌아갑니다. */
    val weekdays: Int = RoutineSchedule.EVERY_DAY,
    /** 정해 둔 시각(분). null이면 상시입니다. */
    val timeOfDay: Int? = null
) {
    companion object {
        fun fromRoutine(routine: RoutineEntity): RoutineDraft {
            return RoutineDraft(
                id = routine.id,
                title = routine.title,
                note = routine.note,
                category = routine.category,
                sourceContentSyncId = routine.sourceContentSyncId,
                reminderEnabled = routine.reminderEnabled,
                weekdays = routine.weekdays,
                timeOfDay = routine.timeOfDay
            )
        }
    }
}
