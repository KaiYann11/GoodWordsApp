package com.codex.appgoodwords.data

import java.time.LocalDate

/**
 * 할 일을 늘어놓는 칸.
 *
 * 예전에는 "지난 일"과 "오늘"만 보여 주었습니다. 내일 날짜로 만들어 두면 그날이 오기 전에는
 * 목록에서 아예 보이지 않아서, 앞일을 적어 두는 곳으로 쓸 수 없었습니다.
 *
 * 차례가 곧 급한 정도입니다. 위에서부터 읽으면 지금 손댈 것이 먼저 옵니다.
 */
enum class TodoBucket(val label: String) {
    OVERDUE("지난 일"),
    TODAY("오늘"),
    THIS_WEEK("이번 주"),
    LATER("나중"),

    /** 마감일을 비워 둔 것. 언젠가 하고 싶은 일입니다. */
    SOMEDAY("언젠가")
}

/**
 * 마감일을 보고 칸을 정합니다.
 *
 * **끝냈는지는 칸을 정하는 데 쓰지 않습니다.** 체크했다고 카드가 다른 칸으로 뛰면
 * 방금 무엇을 눌렀는지 눈으로 놓칩니다. 끝낸 것은 칸 안에서 아래로 내려갈 뿐입니다.
 * 다만 칸 이름 옆의 개수는 **남은 것**만 셉니다. 다 끝낸 칸에 숫자가 붙어 있으면
 * 아직 할 일이 있는 것처럼 보입니다.
 *
 * 순수 함수라 기기 없이 검증할 수 있습니다.
 */
object TodoBuckets {
    /** "이번 주"의 끝. 오늘부터 이만큼까지를 한 칸으로 봅니다. */
    private const val WEEK_DAYS = 7L

    fun bucketOf(todo: TodoEntity, today: LocalDate): TodoBucket {
        val due = parseDate(todo.dueDate) ?: return TodoBucket.SOMEDAY
        return when {
            due.isBefore(today) -> TodoBucket.OVERDUE
            due == today -> TodoBucket.TODAY
            due.isBefore(today.plusDays(WEEK_DAYS)) -> TodoBucket.THIS_WEEK
            else -> TodoBucket.LATER
        }
    }

    /**
     * 칸별로 나눠 담습니다. 빈 칸은 돌려주지 않습니다.
     *
     * 칸 안에서는 남은 것이 먼저, 그다음 마감일 순입니다. 마감일이 같으면 만든 차례입니다.
     * "언젠가"는 마감일이 없으니 만든 차례로만 셉니다.
     */
    fun group(todos: List<TodoEntity>, today: LocalDate): Map<TodoBucket, List<TodoEntity>> {
        return todos
            .groupBy { bucketOf(it, today) }
            .mapValues { (_, list) -> list.sortedWith(inBucketOrder()) }
            // enum 차례대로 내놓습니다. groupBy는 만난 차례를 그대로 두어 화면이 뒤죽박죽입니다.
            .toSortedMap(compareBy { it.ordinal })
    }

    /** 칸 이름 옆에 적을 수. 남은 것만 셉니다. */
    fun remainingCount(todos: List<TodoEntity>): Int = todos.count { !it.isDone }

    private fun inBucketOrder(): Comparator<TodoEntity> = compareBy<TodoEntity> { it.isDone }
        .thenBy { it.dueDate.ifBlank { "9999-12-31" } }
        .thenBy { it.createdAt }

    private fun parseDate(value: String): LocalDate? =
        value.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
