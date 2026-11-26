package com.codex.appgoodwords.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 어제의 나와 비교 — 오늘 지금까지와 어제 하루 전체(사용자가 정함, 2026-11-19). 카드는 한 일만 짚습니다(사용자가 고름):
 * 오늘이 어제보다 많거나 같은 것만 줄로, 적은 것은 숫자 없이 빠지고, 앞선 것이 없으면 "어제처럼 오늘도 …".
 */
class DayComparisonTest {
    private val today = LocalDate.of(2026, 11, 19)

    @Test
    fun onlyWhatTodayMatchedOrBeatShowsAsLines() {
        val comparison = DayComparisons.of(
            today = day(today, routines = 3, quotes = 2, todos = 1, diaries = 0, steps = 4_210),
            yesterday = day(today.minusDays(1), routines = 2, quotes = 2, todos = 0, diaries = 1, steps = 6_800)
        )

        assertTrue(comparison.shown)
        assertEquals(
            listOf("루틴 3번 — 1번 더", "글귀 2개 — 어제만큼", "할 일 1개 끝냄 — 어제는 없었음"),
            comparison.aheadOrEven.map(DayComparisons::line)
        )
        // 적은 것(일기·걸음)은 카드에 숫자로 나오지 않고, 두 날 나란히에는 그대로 있습니다.
        assertEquals(
            listOf(ComparedKind.ROUTINE, ComparedKind.QUOTE, ComparedKind.TODO, ComparedKind.DIARY, ComparedKind.STEPS),
            comparison.rows.map { it.kind }
        )
        assertEquals("6,800", DayComparisons.cell(ComparedKind.STEPS, comparison.rows.last().yesterday))
    }

    @Test
    fun whenNothingIsAheadTheCardSaysWhatBothDaysDidWithoutNumbers() {
        val comparison = DayComparisons.of(
            today = day(today, routines = 1, quotes = 1, steps = 900, walks = 1),
            yesterday = day(today.minusDays(1), routines = 4, quotes = 3, steps = 8_000, walks = 2)
        )

        assertTrue(comparison.aheadOrEven.isEmpty())
        // 걸음과 걷기는 한 번만 말합니다("걷고 걷기를 기록했습니다"가 되지 않게).
        assertEquals("어제처럼 오늘도 루틴을 밟고 글귀를 읽고 걸었습니다.", DayComparisons.sharedLine(comparison))
    }

    @Test
    fun theCardStaysAwayWhenThereIsNothingToCompare() {
        // 오늘 한 것이 없으면 — 아침에 지는 수를 보여 주지 않습니다.
        assertFalse(DayComparisons.of(day(today), day(today.minusDays(1), routines = 3)).shown)
        // 어제가 통째로 비었으면 — "어제는 없었음"만 늘어선 카드가 됩니다.
        assertFalse(DayComparisons.of(day(today, routines = 2), day(today.minusDays(1))).shown)
        assertNull(DayComparisons.sharedLine(DayComparisons.of(day(today, routines = 2), day(today.minusDays(1), quotes = 1))))
    }

    @Test
    fun stepsCountOnlyWhenADayKnowsThemAndReadingCountsBooks() {
        val comparison = DayComparisons.of(
            today = day(today, books = 1, steps = null),
            yesterday = day(today.minusDays(1), books = 1, steps = null)
        )

        assertEquals(listOf(ComparedKind.READING), comparison.rows.map { it.kind })
        assertEquals("책 1권 — 어제만큼", DayComparisons.line(comparison.rows.single()))
    }

    @Test
    fun theAiGetsBothNumbersForEveryKindAnyoneDid() {
        val comparison = DayComparisons.of(
            today = day(today, routines = 3, steps = 4_210),
            yesterday = day(today.minusDays(1), routines = 5, diaries = 1, steps = 6_800)
        )

        assertEquals(
            listOf("루틴: 어제 5번 · 오늘 3번", "일기: 어제 1편 · 오늘 0편", "걸음: 어제 6,800 · 오늘 4,210"),
            DayComparisons.promptLines(comparison)
        )
    }

    /* 홈 글귀 아래 한 줄 띠(사용자가 고름, 2026-11-27). 카드와 같은 생각 — 앞서거나 같은 것만, 적은 것은 숫자 없이. */

    @Test
    fun theStripNamesOnlyWhatTodayMatchedOrBeatAtMostThree() {
        val strip = DayComparisons.strip(
            DayComparisons.of(
                today = day(today, routines = 3, quotes = 2, todos = 1, books = 1, diaries = 0, steps = 8_040),
                yesterday = day(today.minusDays(1), routines = 2, quotes = 2, diaries = 1, steps = 6_800)
            )
        )

        // 루틴 +1 · 글귀 = · 할 일 +1(어제 없음) — 독서·걸음은 넘쳐 "외 2", 적은 일기는 빠집니다.
        assertEquals(DayStrip.Ahead(chips = listOf("루틴 +1", "글귀 =", "할 일 +1"), more = 2), strip)
        assertEquals("루틴 +1 · 글귀 = · 할 일 +1 외 2", DayComparisons.stripText(strip!!))
    }

    @Test
    fun stepsInTheStripAreGroupedAndShownAlone() {
        val strip = DayComparisons.strip(
            DayComparisons.of(day(today, steps = 8_040), day(today.minusDays(1), steps = 6_800))
        )

        assertEquals("걸음 +1,240", DayComparisons.stripText(strip!!))
    }

    @Test
    fun withNothingAheadTheStripSaysWhatBothDaysDidWithoutNumbers() {
        val strip = DayComparisons.strip(
            DayComparisons.of(
                today = day(today, routines = 1, steps = 900, walks = 1),
                yesterday = day(today.minusDays(1), routines = 4, steps = 8_000, walks = 2)
            )
        )

        // 걸음과 걷기는 한 번만 셉니다.
        assertEquals(DayStrip.Shared(listOf(ComparedKind.ROUTINE, ComparedKind.STEPS)), strip)
        assertEquals("어제처럼 오늘도 루틴·걸음", DayComparisons.stripText(strip!!))
    }

    @Test
    fun theStripIsAnEntranceEvenBeforeTodayStartsButNotOnTwoEmptyDays() {
        // 오늘 아직 아무것도 없으면 수 없이 "나란히 봅니다"만 — 아침에 지는 수를 보여 주지 않습니다.
        val morning = DayComparisons.strip(DayComparisons.of(day(today), day(today.minusDays(1), routines = 3)))
        assertEquals(DayStrip.Start, morning)
        assertEquals("어제 하루와 나란히 봅니다", DayComparisons.stripText(morning!!))
        // 두 날 모두 비었으면 띠가 없습니다.
        assertNull(DayComparisons.strip(DayComparisons.of(day(today), day(today.minusDays(1)))))
    }

    private fun day(
        date: LocalDate,
        routines: Int = 0,
        quotes: Int = 0,
        todos: Int = 0,
        diaries: Int = 0,
        books: Int = 0,
        steps: Int? = null,
        walks: Int = 0
    ) = DayDigest(
        score = DayScore(
            date = date,
            steps = DailyStep.DEFAULTS,
            doneSteps = emptySet(),
            routineChecks = routines,
            todosDone = todos,
            diaries = diaries,
            quotesRead = quotes,
            booksRead = books,
            walkSteps = steps
        ),
        routineTitles = emptyList(),
        todoTitles = emptyList(),
        diaryTitles = emptyList(),
        quoteTitles = emptyList(),
        walks = List(walks) { WalkSummary(syncId = "walk-$it", startedAt = it.toLong(), endedAt = it + 1L, distanceMeters = 100, steps = 0) }
    )
}
