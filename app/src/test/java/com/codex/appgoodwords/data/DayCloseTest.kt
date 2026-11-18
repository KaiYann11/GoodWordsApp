package com.codex.appgoodwords.data

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 하루 마무리(사용자가 정함, 2026-11-19 — 알림을 마무리로 + 홈, 한 장에 네 칸). 새 기록 종류 없이 감사 일기·내일 할 일로 남고,
 * 마무리한 날만 이 기기에 적어 홈 카드와 알림을 그날은 거둡니다.
 */
class DayCloseTest {
    private val today = LocalDate.of(2026, 11, 19)

    @Test
    fun theCardComesAfterTheCloseTimeUntilTheDayIsClosed() {
        val before = LocalDateTime.of(2026, 11, 19, 20, 59)
        val at = LocalDateTime.of(2026, 11, 19, 21, 0)
        assertFalse(DayClose.isDue(before, hour = 21, minute = 0, closedDate = ""))
        assertTrue(DayClose.isDue(at, hour = 21, minute = 0, closedDate = ""))
        // 분까지 봅니다(설정의 시각은 시·분입니다).
        assertFalse(DayClose.isDue(LocalDateTime.of(2026, 11, 19, 21, 29), hour = 21, minute = 30, closedDate = ""))
        // 마무리한 날은 사라지고, 어제 마무리한 것은 오늘을 막지 않습니다.
        assertFalse(DayClose.isDue(at, hour = 21, minute = 0, closedDate = "2026-11-19"))
        assertTrue(DayClose.isDue(at, hour = 21, minute = 0, closedDate = "2026-11-18"))
    }

    @Test
    fun theDoneLineCountsWhatWasDoneInTheSameOrderAsTheComparison() {
        val busy = day(routines = 3, quotes = 2, todos = 1, diaries = 1, steps = 6_240)
        // 넷까지 — 알림 한 줄에 다 들어가게.
        assertEquals("루틴 3번 · 글귀 2개 · 할 일 1개 끝냄 · 일기 1편", DayClose.doneLine(busy))
        assertEquals("오늘 루틴 3번 · 글귀 2개 · 할 일 1개 끝냄 · 일기 1편", DayClose.notificationText(busy))
        assertEquals("6,240걸음", DayClose.doneLine(day(steps = 6_240)))
        // 한 것이 없는 날도 마무리할 수 있습니다. 못 한 것을 세지 않습니다.
        assertNull(DayClose.doneLine(day()))
        assertEquals("오늘을 정리해 볼까요?", DayClose.notificationText(day()))
    }

    @Test
    fun theGratitudeLineBecomesTodaysGratitudeDiaryAndTheTodoGoesToTomorrow() {
        val diary = DayClose.gratitudeDiary("  비 그친 하늘  ", today)!!
        assertEquals(DiaryKind.GRATITUDE.name, diary.kind)
        assertEquals(listOf("비 그친 하늘"), diary.answers)
        assertEquals(today, diary.entryDate)
        assertNull(DayClose.gratitudeDiary("   ", today))

        val todo = DayClose.tomorrowTodo(" 우체국 가기 ", today)!!
        assertEquals("우체국 가기", todo.title)
        assertEquals(today.plusDays(1), todo.dueDate)
        assertNull(DayClose.tomorrowTodo("", today))
    }

    @Test
    fun theFinishedMessageSaysWhatWasWritten() {
        assertEquals("하루를 마무리했습니다", DayClose.finishedMessage(wroteGratitude = false, wroteTodo = false))
        assertEquals("하루를 마무리했습니다 · 감사 일기를 적었습니다", DayClose.finishedMessage(wroteGratitude = true, wroteTodo = false))
        assertEquals("하루를 마무리했습니다 · 내일 할 일 1개를 적었습니다", DayClose.finishedMessage(wroteGratitude = false, wroteTodo = true))
        assertEquals(
            "하루를 마무리했습니다 · 감사 일기와 내일 할 일 1개를 적었습니다",
            DayClose.finishedMessage(wroteGratitude = true, wroteTodo = true)
        )
        assertEquals("11월 19일(목)", DayClose.dateLabel(today))
    }

    private fun day(
        routines: Int = 0,
        quotes: Int = 0,
        todos: Int = 0,
        diaries: Int = 0,
        steps: Int? = null
    ) = DayDigest(
        score = DayScore(
            date = today,
            steps = DailyStep.DEFAULTS,
            doneSteps = emptySet(),
            routineChecks = routines,
            todosDone = todos,
            diaries = diaries,
            quotesRead = quotes,
            walkSteps = steps
        ),
        routineTitles = emptyList(),
        todoTitles = emptyList(),
        diaryTitles = emptyList(),
        quoteTitles = emptyList()
    )
}
