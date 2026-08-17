package com.codex.appgoodwords.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsCalculatorTest {
    @Test
    fun readingCountsSeparateWhatIsDoneFromWhatIsOpen() {
        val today = LocalDate.of(2026, 8, 18)
        val summary = StatsCalculator.build(
            events = emptyList(),
            items = emptyList(),
            routineChecks = emptyList(),
            today = today,
            zoneId = zone,
            books = listOf(
                BookEntity(syncId = "b1", title = "읽는 중", totalPages = 300, currentPage = 100),
                BookEntity(
                    syncId = "b2",
                    title = "올해 완독",
                    status = BookStatus.FINISHED.name,
                    totalPages = 200,
                    currentPage = 200,
                    finishedAt = millisAt(LocalDate.of(2026, 3, 1))
                ),
                BookEntity(
                    syncId = "b3",
                    title = "작년 완독",
                    status = BookStatus.FINISHED.name,
                    totalPages = 150,
                    currentPage = 150,
                    finishedAt = millisAt(LocalDate.of(2025, 12, 1))
                )
            )
        )

        assertEquals(1, summary.reading.readingCount)
        assertEquals(2, summary.reading.finishedCount)
        assertEquals("올해 읽은 것만 세야 합니다.", 1, summary.reading.finishedThisYear)
        assertEquals(450, summary.reading.pagesRead)
    }

    @Test
    fun pagesAreOnlyCountedWhenTheTotalIsKnown() {
        val summary = StatsCalculator.build(
            events = emptyList(),
            items = emptyList(),
            routineChecks = emptyList(),
            today = LocalDate.of(2026, 8, 18),
            books = listOf(
                BookEntity(syncId = "b1", title = "쪽수 모르는 책", currentPage = 999),
                BookEntity(syncId = "b2", title = "쪽수 아는 책", totalPages = 100, currentPage = 40)
            )
        )

        // 전체를 모르는 책의 쪽수는 짐작입니다. 합치면 숫자가 거짓말이 됩니다.
        assertEquals(40, summary.reading.pagesRead)
    }

    @Test
    fun quotesTakenFromBooksAreCounted() {
        val summary = StatsCalculator.build(
            events = emptyList(),
            items = listOf(
                ContentItemEntity(syncId = "q1", type = ContentType.QUOTE, title = "책", body = "본문", bookSyncId = "b1"),
                ContentItemEntity(syncId = "q2", type = ContentType.QUOTE, title = "그냥", body = "본문")
            ),
            routineChecks = emptyList(),
            today = LocalDate.of(2026, 8, 18),
            books = listOf(BookEntity(syncId = "b1", title = "책"))
        )

        assertEquals(1, summary.reading.quotesFromBooks)
    }

    @Test
    fun diaryCountsDaysNotEntries() {
        val today = LocalDate.of(2026, 8, 18)
        val summary = StatsCalculator.build(
            events = emptyList(),
            items = emptyList(),
            routineChecks = emptyList(),
            today = today,
            diaries = listOf(
                DiaryEntity(syncId = "d1", entryDate = "2026-08-17", mood = DiaryMood.GOOD.name),
                DiaryEntity(syncId = "d2", entryDate = "2026-08-17", mood = DiaryMood.GOOD.name),
                DiaryEntity(syncId = "d3", entryDate = "2026-08-18", mood = DiaryMood.TIRED.name),
                DiaryEntity(syncId = "d4", entryDate = "2026-07-30")
            )
        )

        assertEquals(4, summary.diary.totalCount)
        // 하루에 두 번 써도 하루입니다. "이 달에 며칠 썼나"가 궁금한 것입니다.
        assertEquals(2, summary.diary.daysThisMonth)
        assertEquals(DiaryMood.GOOD, summary.diary.topMoods.first().mood)
        assertEquals(2, summary.diary.topMoods.first().count)
    }

    @Test
    fun todoRatioIsUnknownWhenThereAreNone() {
        val summary = StatsCalculator.build(
            events = emptyList(),
            items = emptyList(),
            routineChecks = emptyList(),
            today = LocalDate.of(2026, 8, 18)
        )

        // 0%라고 적으면 다 못 끝낸 것처럼 보입니다. 아무것도 없는 것과는 다릅니다.
        assertNull(summary.todo.doneRatio)
    }

    @Test
    fun todoCountsSeparateOverdueFromOpen() {
        val today = LocalDate.of(2026, 8, 18)
        val summary = StatsCalculator.build(
            events = emptyList(),
            items = emptyList(),
            routineChecks = emptyList(),
            today = today,
            todos = listOf(
                TodoEntity(syncId = "t1", title = "끝낸 일", dueDate = "2026-08-17", doneAt = 5_000L),
                TodoEntity(syncId = "t2", title = "오늘 일", dueDate = "2026-08-18"),
                TodoEntity(syncId = "t3", title = "밀린 일", dueDate = "2026-08-10")
            )
        )

        assertEquals(1, summary.todo.doneCount)
        assertEquals(2, summary.todo.openCount)
        assertEquals(1, summary.todo.overdueCount)
        assertEquals(1f / 3f, summary.todo.doneRatio!!, 0.001f)
    }

    @Test
    fun aDayWithOnlyADiaryStillCounts() {
        val today = LocalDate.of(2026, 8, 18)
        val summary = StatsCalculator.build(
            events = emptyList(),
            items = emptyList(),
            routineChecks = emptyList(),
            today = today,
            diaries = listOf(
                DiaryEntity(syncId = "d1", entryDate = "2026-08-18"),
                DiaryEntity(syncId = "d2", entryDate = "2026-08-17")
            )
        )

        // 글귀만 보면 일기만 쓴 날이 "아무것도 안 한 날"이 됩니다.
        assertEquals(2, summary.activeDays)
        assertEquals(2, summary.currentStreakDays)
        assertTrue(summary.hasActivity)
    }

    @Test
    fun recentDaysIncludeDiariesAndFinishedTodos() {
        val today = LocalDate.of(2026, 8, 18)
        val summary = StatsCalculator.build(
            events = emptyList(),
            items = emptyList(),
            routineChecks = emptyList(),
            today = today,
            zoneId = zone,
            diaries = listOf(DiaryEntity(syncId = "d1", entryDate = "2026-08-18")),
            todos = listOf(
                TodoEntity(syncId = "t1", title = "끝낸 일", dueDate = "2026-08-18", doneAt = millisAt(today))
            )
        )

        val todayBar = summary.recentDays.last()
        assertEquals(1, todayBar.diaryCount)
        assertEquals(1, todayBar.todoDoneCount)
        assertEquals(2, todayBar.total)
    }

    @Test
    fun aBrokenDiaryDateDoesNotBreakTheSummary() {
        val summary = StatsCalculator.build(
            events = emptyList(),
            items = emptyList(),
            routineChecks = emptyList(),
            today = LocalDate.of(2026, 8, 18),
            diaries = listOf(DiaryEntity(syncId = "d1", entryDate = "언젠가"))
        )

        // 다른 기기에서 이상한 값이 넘어와도 돌아보기 화면이 죽으면 안 됩니다.
        assertEquals(1, summary.diary.totalCount)
        assertEquals(0, summary.diary.daysThisMonth)
    }

    private val zone: ZoneId = ZoneId.of("Asia/Seoul")
    private val today: LocalDate = LocalDate.of(2026, 8, 5)

    @Test
    fun streak_countsConsecutiveDaysEndingToday() {
        val events = listOf(
            confirmedOn(today),
            confirmedOn(today.minusDays(1)),
            confirmedOn(today.minusDays(2))
        )

        val summary = build(events = events)

        assertEquals(3, summary.currentStreakDays)
    }

    @Test
    fun streak_survivesWhenTodayHasNoActivityYet() {
        // 아침에 열었을 때 어제까지의 연속이 0으로 보이면 안 된다.
        val events = listOf(
            confirmedOn(today.minusDays(1)),
            confirmedOn(today.minusDays(2))
        )

        val summary = build(events = events)

        assertEquals(2, summary.currentStreakDays)
    }

    @Test
    fun streak_breaksWhenAFullDayIsMissed() {
        val events = listOf(
            confirmedOn(today.minusDays(2)),
            confirmedOn(today.minusDays(3))
        )

        val summary = build(events = events)

        assertEquals(0, summary.currentStreakDays)
    }

    @Test
    fun streak_countsRoutineCheckAsActivity() {
        val summary = build(
            events = listOf(confirmedOn(today)),
            checks = listOf(checkOn(today.minusDays(1)))
        )

        assertEquals(2, summary.currentStreakDays)
    }

    @Test
    fun streak_multipleEventsOnSameDayCountOnce() {
        val events = listOf(
            confirmedOn(today),
            confirmedOn(today),
            confirmedOn(today)
        )

        val summary = build(events = events)

        assertEquals(1, summary.currentStreakDays)
    }

    @Test
    fun bestStreak_findsLongestRunInHistory() {
        val events = listOf(
            confirmedOn(today.minusDays(20)),
            confirmedOn(today.minusDays(19)),
            confirmedOn(today.minusDays(18)),
            confirmedOn(today.minusDays(17)),
            confirmedOn(today),
            confirmedOn(today.minusDays(1))
        )

        val summary = build(events = events)

        assertEquals(4, summary.bestStreakDays)
        assertEquals(2, summary.currentStreakDays)
    }

    @Test
    fun ignoresNonConfirmedEvents() {
        val events = listOf(
            event(today, ExposureEventType.SURFACED),
            event(today, ExposureEventType.SHOWN)
        )

        val summary = build(events = events)

        assertEquals(0, summary.currentStreakDays)
        assertEquals(0, summary.confirmedTotal)
        assertFalse(summary.hasActivity)
    }

    @Test
    fun recentDays_alwaysCoversSevenDaysEndingToday() {
        val summary = build(events = listOf(confirmedOn(today.minusDays(3))))

        assertEquals(StatsCalculator.RECENT_DAY_COUNT, summary.recentDays.size)
        assertEquals(today.minusDays(6), summary.recentDays.first().date)
        assertEquals(today, summary.recentDays.last().date)
        assertEquals(1, summary.recentDays.single { it.date == today.minusDays(3) }.confirmedCount)
        assertEquals(0, summary.recentDays.single { it.date == today }.total)
    }

    @Test
    fun recentDays_splitsConfirmedAndRoutineCounts() {
        val summary = build(
            events = listOf(confirmedOn(today), confirmedOn(today)),
            checks = listOf(checkOn(today))
        )

        val todayRow = summary.recentDays.single { it.date == today }
        assertEquals(2, todayRow.confirmedCount)
        assertEquals(1, todayRow.routineCheckCount)
        assertEquals(3, todayRow.total)
    }

    @Test
    fun topCategories_rankByCountThenName() {
        val items = listOf(
            item(id = 1L, category = "동기부여"),
            item(id = 2L, category = "습관"),
            item(id = 3L, category = "건강")
        )
        val events = listOf(
            confirmedOn(today, itemId = 1L),
            confirmedOn(today, itemId = 1L),
            confirmedOn(today, itemId = 2L),
            confirmedOn(today, itemId = 3L)
        )

        val summary = build(events = events, items = items)

        assertEquals(
            listOf(
                CategoryCount("동기부여", 2),
                CategoryCount("건강", 1),
                CategoryCount("습관", 1)
            ),
            summary.topCategories
        )
    }

    @Test
    fun topCategories_skipDeletedItemsAndBlankCategories() {
        val items = listOf(
            item(id = 1L, category = "동기부여"),
            item(id = 2L, category = "   ")
        )
        val events = listOf(
            confirmedOn(today, itemId = 1L),
            confirmedOn(today, itemId = 2L),
            confirmedOn(today, itemId = 99L)
        )

        val summary = build(events = events, items = items)

        assertEquals(listOf(CategoryCount("동기부여", 1)), summary.topCategories)
    }

    @Test
    fun emptyInputProducesZeroedSummary() {
        val summary = build()

        assertEquals(0, summary.currentStreakDays)
        assertEquals(0, summary.bestStreakDays)
        assertEquals(0, summary.activeDays)
        assertTrue(summary.topCategories.isEmpty())
        assertFalse(summary.hasActivity)
        assertEquals(StatsCalculator.RECENT_DAY_COUNT, summary.recentDays.size)
    }

    private fun build(
        events: List<ExposureEventEntity> = emptyList(),
        items: List<ContentItemEntity> = emptyList(),
        checks: List<RoutineCheckEntity> = emptyList()
    ): StatsSummary = StatsCalculator.build(
        events = events,
        items = items,
        routineChecks = checks,
        today = today,
        zoneId = zone
    )

    private fun millisAt(date: LocalDate): Long = date
        .atTime(LocalTime.NOON)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    private fun confirmedOn(date: LocalDate, itemId: Long = 1L): ExposureEventEntity =
        event(date, ExposureEventType.CONFIRMED, itemId)

    private fun event(
        date: LocalDate,
        eventType: ExposureEventType,
        itemId: Long = 1L
    ): ExposureEventEntity = ExposureEventEntity(
        contentItemId = itemId,
        contentTitle = "제목",
        contentType = ContentType.QUOTE,
        eventType = eventType,
        trigger = ExposureTrigger.MANUAL_REFRESH,
        occurredAt = millisAt(date)
    )

    private fun checkOn(date: LocalDate): RoutineCheckEntity = RoutineCheckEntity(
        routineId = 1L,
        routineTitle = "루틴",
        checkedAt = millisAt(date)
    )

    private fun item(id: Long, category: String): ContentItemEntity = ContentItemEntity(
        id = id,
        type = ContentType.QUOTE,
        title = "제목 $id",
        body = "본문",
        category = category
    )
}
