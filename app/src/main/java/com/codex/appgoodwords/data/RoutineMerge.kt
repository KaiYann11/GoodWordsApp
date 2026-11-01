package com.codex.appgoodwords.data

/**
 * 루틴 합치기에서 이름과 메모를 무엇으로 할지(사용자가 정함, 2026-11-02).
 *
 * 이름과 메모는 칸마다 이것(고치던 루틴)·그것(고른 루틴)·둘 다 중에 고르고, 고른 글을 그대로 고칠 수 있습니다.
 * 고른 뒤 손으로 고치면 어느 것도 아닌 글이 됩니다 — 그것은 화면이 들고 있고, 여기서는 고를 때의 글만 만듭니다.
 */
enum class RoutineMergeChoice {
    /** 고치던 루틴(남는 쪽)의 것. */
    THIS,

    /** 고른 루틴(합쳐져 사라지는 쪽)의 것. */
    THAT,

    BOTH
}

object RoutineMergeText {
    /** 이름은 한 줄입니다. 둘 다면 가운뎃점으로 잇습니다. 같은 이름을 두 번 적지 않습니다. */
    fun title(choice: RoutineMergeChoice, mine: String, theirs: String): String =
        pick(choice, mine, theirs, separator = " · ")

    /** 메모는 여러 줄입니다. 둘 다면 빈 줄을 사이에 두고 잇습니다. 비어 있는 쪽은 빼고 잇습니다. */
    fun note(choice: RoutineMergeChoice, mine: String, theirs: String): String =
        pick(choice, mine, theirs, separator = "\n\n")

    private fun pick(choice: RoutineMergeChoice, mine: String, theirs: String, separator: String): String = when (choice) {
        RoutineMergeChoice.THIS -> mine.trim()
        RoutineMergeChoice.THAT -> theirs.trim()
        RoutineMergeChoice.BOTH -> listOf(mine.trim(), theirs.trim()).filter { it.isNotBlank() }.distinct().joinToString(separator)
    }
}

/** 합친 결과. 몇 개를 옮겼는지는 알림 문구에 씁니다. */
data class RoutineMergeResult(
    val routine: RoutineEntity,
    val movedChecks: Int,
    val movedMemos: Int
)
