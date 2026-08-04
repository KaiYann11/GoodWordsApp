package com.codex.appgoodwords.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsCalculatorTest {
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
