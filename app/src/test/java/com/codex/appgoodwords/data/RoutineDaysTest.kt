package com.codex.appgoodwords.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 요일 알약이 가리키는 날과 지난날에 적는 체크(사용자가 정함, 2026-11-18).
 *
 * 알약은 일~토 차례 그대로이고 저마다 그 요일이 마지막으로 있었던 날을 가리킵니다. **앞날을 가리키는 알약이 없어야**
 * 합니다 — 앞날에 체크가 적히면 연속 날수와 오늘 진행이 거짓말을 합니다.
 */
class RoutineDaysTest {
    private val seoul = ZoneId.of("Asia/Seoul")

    /** 2026-11-18은 수요일입니다. */
    private val wednesday = LocalDate.of(2026, 11, 18)

    @Test
    fun eachPillPointsAtTheLatestDayWithThatWeekday() {
        assertEquals(LocalDate.of(2026, 11, 15), RoutineDays.latest(DayOfWeek.SUNDAY, wednesday))
        assertEquals(LocalDate.of(2026, 11, 16), RoutineDays.latest(DayOfWeek.MONDAY, wednesday))
        assertEquals(LocalDate.of(2026, 11, 17), RoutineDays.latest(DayOfWeek.TUESDAY, wednesday))
        assertEquals(wednesday, RoutineDays.latest(DayOfWeek.WEDNESDAY, wednesday))
        // 오늘보다 뒤 요일은 지난주 날입니다.
        assertEquals(LocalDate.of(2026, 11, 12), RoutineDays.latest(DayOfWeek.THURSDAY, wednesday))
        assertEquals(LocalDate.of(2026, 11, 13), RoutineDays.latest(DayOfWeek.FRIDAY, wednesday))
        assertEquals(LocalDate.of(2026, 11, 14), RoutineDays.latest(DayOfWeek.SATURDAY, wednesday))
    }

    @Test
    fun theSevenPillsAreTodayAndTheSixDaysBeforeOnEveryDayOfTheWeek() {
        (0L..6L).map { wednesday.plusDays(it) }.forEach { today ->
            val pointed = RoutineStatsCalculator.WEEK_ORDER.map { RoutineDays.latest(it, today) }.toSet()
            val lastWeek = (0L..RoutineDays.PAST_DAYS).map { today.minusDays(it) }.toSet()
            assertEquals("$today", lastWeek, pointed)
            assertTrue("$today: 앞날을 가리키는 알약이 있습니다.", pointed.none { it.isAfter(today) })
        }
    }

    @Test
    fun aCheckForTodayIsNow() {
        val now = millis(LocalDateTime.of(2026, 11, 18, 14, 30, 5), seoul) + 123

        assertEquals(now, RoutineDays.checkTime(wednesday, now, seoul))
    }

    @Test
    fun aCheckForAPastDayBorrowsTheClockTimeOnThatDay() {
        val now = millis(LocalDateTime.of(2026, 11, 18, 14, 30, 5), seoul) + 123

        val checkedAt = RoutineDays.checkTime(LocalDate.of(2026, 11, 16), now, seoul)

        assertEquals(millis(LocalDateTime.of(2026, 11, 16, 14, 30, 5), seoul) + 123, checkedAt)
    }

    @Test
    fun justAfterMidnightYesterdaysCheckStaysOnYesterday() {
        // 자정 넘어 어제 것을 적으면 어제 00:05가 됩니다. 그날을 벗어나지만 않으면 됩니다.
        val now = millis(LocalDateTime.of(2026, 11, 18, 0, 5), seoul)

        val checkedAt = RoutineDays.checkTime(LocalDate.of(2026, 11, 17), now, seoul)

        assertEquals(LocalDate.of(2026, 11, 17), Instant.ofEpochMilli(checkedAt).atZone(seoul).toLocalDate())
    }

    @Test
    fun aClockTimeThatDoesNotExistThatDayStillLandsOnThatDay() {
        // 서머타임으로 02:00~03:00이 없는 날(뉴욕 2026-03-08)에도 그날 안에 적혀야 합니다.
        val newYork = ZoneId.of("America/New_York")
        val now = millis(LocalDateTime.of(2026, 3, 10, 2, 30), newYork)

        val checkedAt = RoutineDays.checkTime(LocalDate.of(2026, 3, 8), now, newYork)

        assertEquals(LocalDate.of(2026, 3, 8), Instant.ofEpochMilli(checkedAt).atZone(newYork).toLocalDate())
    }

    @Test
    fun countsAreKeptPerDayAndPerRoutineInsideTheRangeOnly() {
        val checks = listOf(
            check(1, LocalDateTime.of(2026, 11, 16, 7, 0)),
            check(1, LocalDateTime.of(2026, 11, 16, 21, 0)),
            check(2, LocalDateTime.of(2026, 11, 16, 23, 59)),
            check(2, LocalDateTime.of(2026, 11, 17, 0, 0)),
            // 범위 밖입니다.
            check(1, LocalDateTime.of(2026, 11, 11, 9, 0)),
            check(1, LocalDateTime.of(2026, 11, 18, 9, 0))
        )

        val counts = RoutineDays.countsByDate(checks, LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17), seoul)

        assertEquals(
            mapOf(
                LocalDate.of(2026, 11, 16) to mapOf(1L to 2, 2L to 1),
                LocalDate.of(2026, 11, 17) to mapOf(2L to 1)
            ),
            counts
        )
    }

    @Test
    fun theDayIsCalledByItsDateAndWeekday() {
        assertEquals("16일(월)", RoutineDays.label(LocalDate.of(2026, 11, 16)))
        assertEquals("오늘", RoutineDays.spoken(wednesday, wednesday))
        assertEquals("17일(화)", RoutineDays.spoken(LocalDate.of(2026, 11, 17), wednesday))
    }

    @Test
    fun aCheckWrittenForAPastDayIsMissedByTheChangedOnlyPush() {
        // 그래서 지난날에 적으면 다음 동기화를 통째로 보냅니다(SettingsStore.requestFullPush).
        val lastPush = millis(LocalDateTime.of(2026, 11, 18, 8, 0), seoul)
        val now = millis(LocalDateTime.of(2026, 11, 18, 14, 30), seoul)
        val backfilled = check(1, LocalDateTime.of(2026, 11, 16, 14, 30)).copy(syncId = "backfilled")
        val snapshot = AppDataSnapshot(
            items = emptyList(),
            events = emptyList(),
            routines = emptyList(),
            routineChecks = listOf(backfilled, check(1, LocalDateTime.of(2026, 11, 18, 14, 30)).copy(syncId = "today")),
            routineMemos = emptyList(),
            settings = ReminderSettings()
        )

        assertTrue(now > lastPush)
        assertEquals(listOf("today"), SyncDelta.changedSince(snapshot, lastPush).routineChecks.map { it.syncId })
        assertEquals(2, SyncDelta.changedSince(snapshot, 0L).routineChecks.size)
    }

    private fun check(routineId: Long, at: LocalDateTime) = RoutineCheckEntity(
        syncId = "check-$routineId-$at",
        routineId = routineId,
        routineSyncId = "routine-$routineId",
        routineTitle = "루틴 $routineId",
        checkedAt = millis(at, seoul)
    )

    private fun millis(at: LocalDateTime, zone: ZoneId): Long = at.atZone(zone).toInstant().toEpochMilli()
}
