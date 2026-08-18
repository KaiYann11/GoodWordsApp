package com.codex.appgoodwords.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 위젯 아래 한두 줄에 무엇을 남길지 정하는 규칙입니다.
 *
 * 위젯은 좁아서, 다 보여 주려 하면 글귀가 밀려나 정작 이 위젯을 놓은 이유가 사라집니다.
 * 그래서 여기서 보는 것은 "지금 해야 할 것만 남기는가"입니다.
 */
class WidgetSummaryTest {
    private val today = LocalDate.of(2026, 8, 18)

    @Test
    fun withNothingToDoTheLineIsSkipped() {
        val summary = WidgetSummary.of(todos = emptyList(), books = emptyList(), today = today)

        assertTrue(summary.isEmpty)
        assertFalse(summary.hasTodos)
        assertFalse(summary.hasBook)
    }

    @Test
    fun finishedWorkIsNotShown() {
        val summary = WidgetSummary.of(
            todos = listOf(
                todo("끝낸 일", due = "2026-08-18", doneAt = 5_000L),
                todo("남은 일", due = "2026-08-18")
            ),
            books = listOf(
                BookEntity(syncId = "b1", title = "다 읽은 책", status = BookStatus.FINISHED.name)
            ),
            today = today
        )

        // 끝낸 일과 다 읽은 책은 지금 할 것이 아닙니다.
        assertEquals(1, summary.remainingTodos)
        assertFalse(summary.hasBook)
    }

    @Test
    fun overdueWorkCountsAsTodays() {
        val summary = WidgetSummary.of(
            todos = listOf(
                todo("밀린 일", due = "2026-08-10"),
                todo("오늘 일", due = "2026-08-18")
            ),
            books = emptyList(),
            today = today
        )

        assertEquals(2, summary.remainingTodos)
        assertEquals(1, summary.overdueTodos)
        assertEquals("할 일 2개 · 지난 일 1개", summary.todoLine)
    }

    @Test
    fun futureWorkIsNotUrgent() {
        val summary = WidgetSummary.of(
            todos = listOf(todo("내일 일", due = "2026-08-19")),
            books = emptyList(),
            today = today
        )

        // 아직 안 와도 되는 일까지 세면 매일 숫자가 부풀어 뜻이 없어집니다.
        assertEquals(0, summary.remainingTodos)
    }

    @Test
    fun withoutOverdueTheLineStaysShort() {
        val summary = WidgetSummary.of(
            todos = listOf(todo("오늘 일", due = "2026-08-18")),
            books = emptyList(),
            today = today
        )

        assertEquals("할 일 1개", summary.todoLine)
    }

    @Test
    fun theMostRecentlyTouchedBookIsShown() {
        val summary = WidgetSummary.of(
            todos = emptyList(),
            books = listOf(
                BookEntity(syncId = "b1", updatedAt = 1_000L, title = "예전에 보던 책"),
                BookEntity(syncId = "b2", updatedAt = 5_000L, title = "지금 보는 책", totalPages = 200, currentPage = 68)
            ),
            today = today
        )

        // 여러 권을 함께 읽어도 지금 보는 것은 하나입니다.
        assertEquals("지금 보는 책", summary.readingTitle)
        assertEquals(34, summary.readingPercent)
        assertEquals("읽는 중 · 지금 보는 책 34%", summary.bookLine)
    }

    @Test
    fun withoutATotalTheBookLineHasNoPercent() {
        val summary = WidgetSummary.of(
            todos = emptyList(),
            books = listOf(BookEntity(syncId = "b1", title = "쪽수 모르는 책", currentPage = 50)),
            today = today
        )

        // 0%라고 쓰면 안 읽은 것처럼 보입니다.
        assertNull(summary.readingPercent)
        assertEquals("읽는 중 · 쪽수 모르는 책", summary.bookLine)
    }

    private fun todo(title: String, due: String, doneAt: Long? = null) = TodoEntity(
        syncId = "t-$title",
        title = title,
        dueDate = due,
        doneAt = doneAt
    )
}
