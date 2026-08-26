package com.codex.appgoodwords.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 할 일을 늘어놓는 칸입니다.
 *
 * 예전에는 지난 일과 오늘만 보였습니다. 내일 날짜로 만들어 두면 그날이 오기 전에는
 * 목록에서 아예 보이지 않아, 앞일을 적어 두는 곳으로 쓸 수 없었습니다.
 */
class TodoBucketsTest {
    private val today = LocalDate.of(2026, 8, 26)

    @Test
    fun everyDayFindsItsPlace() {
        assertEquals(TodoBucket.OVERDUE, bucketOf(todo(due = "2026-08-25")))
        assertEquals(TodoBucket.TODAY, bucketOf(todo(due = "2026-08-26")))
        assertEquals(TodoBucket.THIS_WEEK, bucketOf(todo(due = "2026-08-27")))
        assertEquals(TodoBucket.THIS_WEEK, bucketOf(todo(due = "2026-09-01")))
        // 이레째부터는 "나중"입니다.
        assertEquals(TodoBucket.LATER, bucketOf(todo(due = "2026-09-02")))
    }

    @Test
    fun aTodoWithoutADateIsSomeday() {
        assertEquals(TodoBucket.SOMEDAY, bucketOf(todo(due = "")))
        // 읽을 수 없는 날짜가 들어와도 버리지 않습니다. 지우면 다음 업로드에서 서버 값까지 사라집니다.
        assertEquals(TodoBucket.SOMEDAY, bucketOf(todo(due = "언젠가")))
    }

    @Test
    fun finishingSomethingDoesNotMakeItJumpToAnotherBucket() {
        // 체크했다고 카드가 다른 칸으로 뛰면 방금 무엇을 눌렀는지 눈으로 놓칩니다.
        val done = todo(due = "2026-08-20", doneAt = 1_000L)

        assertEquals(TodoBucket.OVERDUE, bucketOf(done))
    }

    @Test
    fun withinABucketTheRemainingOnesComeFirst() {
        val grouped = TodoBuckets.group(
            listOf(
                todo(id = 1, due = "2026-08-26", doneAt = 5L),
                todo(id = 2, due = "2026-08-26")
            ),
            today
        )

        assertEquals(listOf(2L, 1L), grouped.getValue(TodoBucket.TODAY).map { it.id })
    }

    @Test
    fun theHeaderCountsOnlyWhatIsLeft() {
        // 다 끝낸 칸에 숫자가 붙어 있으면 아직 할 일이 있는 것처럼 보입니다.
        val list = listOf(todo(id = 1, doneAt = 5L), todo(id = 2, doneAt = 6L), todo(id = 3))

        assertEquals(1, TodoBuckets.remainingCount(list))
    }

    @Test
    fun bucketsComeOutInTheOrderTheyAreRead() {
        val grouped = TodoBuckets.group(
            listOf(
                todo(id = 1, due = ""),
                todo(id = 2, due = "2026-12-01"),
                todo(id = 3, due = "2026-08-20"),
                todo(id = 4, due = "2026-08-26")
            ),
            today
        )

        assertEquals(
            listOf(TodoBucket.OVERDUE, TodoBucket.TODAY, TodoBucket.LATER, TodoBucket.SOMEDAY),
            grouped.keys.toList()
        )
    }

    @Test
    fun emptyBucketsAreNotShown() {
        val grouped = TodoBuckets.group(listOf(todo(due = "2026-08-26")), today)

        assertEquals(setOf(TodoBucket.TODAY), grouped.keys)
    }

    @Test
    fun anEarlierDeadlineComesFirst() {
        val grouped = TodoBuckets.group(
            listOf(
                todo(id = 1, due = "2026-09-01"),
                todo(id = 2, due = "2026-08-27")
            ),
            today
        )

        assertEquals(listOf(2L, 1L), grouped.getValue(TodoBucket.THIS_WEEK).map { it.id })
    }

    @Test
    fun somedayIsOrderedByWhenItWasWritten() {
        val grouped = TodoBuckets.group(
            listOf(
                todo(id = 1, due = "", createdAt = 200L),
                todo(id = 2, due = "", createdAt = 100L)
            ),
            today
        )

        assertTrue(grouped.getValue(TodoBucket.SOMEDAY).map { it.id } == listOf(2L, 1L))
    }

    private fun bucketOf(todo: TodoEntity) = TodoBuckets.bucketOf(todo, today)

    private fun todo(
        id: Long = 1,
        due: String = "2026-08-26",
        doneAt: Long? = null,
        createdAt: Long = 0L
    ) = TodoEntity(
        id = id,
        syncId = "todo-$id",
        updatedAt = 0L,
        title = "할 일 $id",
        dueDate = due,
        doneAt = doneAt,
        createdAt = createdAt
    )
}
