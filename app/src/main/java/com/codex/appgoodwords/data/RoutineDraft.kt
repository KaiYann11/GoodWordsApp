package com.codex.appgoodwords.data

data class RoutineDraft(
    val id: Long = 0,
    val title: String = "",
    val note: String = "",
    val category: String = "",
    /** 이 루틴을 뽑아낸 글귀. 직접 만든 루틴이면 빈 문자열입니다. */
    val sourceContentSyncId: String = "",
    val reminderEnabled: Boolean = true
) {
    companion object {
        fun fromRoutine(routine: RoutineEntity): RoutineDraft {
            return RoutineDraft(
                id = routine.id,
                title = routine.title,
                note = routine.note,
                category = routine.category,
                sourceContentSyncId = routine.sourceContentSyncId,
                reminderEnabled = routine.reminderEnabled
            )
        }
    }
}
