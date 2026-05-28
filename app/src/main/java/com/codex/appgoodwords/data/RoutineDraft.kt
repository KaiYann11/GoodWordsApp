package com.codex.appgoodwords.data

data class RoutineDraft(
    val id: Long = 0,
    val title: String = "",
    val note: String = "",
    val category: String = "",
    val reminderEnabled: Boolean = true
) {
    companion object {
        fun fromRoutine(routine: RoutineEntity): RoutineDraft {
            return RoutineDraft(
                id = routine.id,
                title = routine.title,
                note = routine.note,
                category = routine.category,
                reminderEnabled = routine.reminderEnabled
            )
        }
    }
}
