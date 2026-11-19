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
 * 요일 알약이 가리키는 날과 지난날에 적는 체크.
 *
 * 요일 줄은 월~일 한 주입니다(사용자가 정함, 2026-11-19). 알약마다 보이는 주의 그날을 가리키고, 오늘 뒤 날은 아직 오지 않은
 * 날입니다. **지난주 같은 요일로 미끄러지지 않아야** 합니다 — 1.24.1은 오늘 뒤 요일이 지난주 날을 가리켜, 이번 주 금요일
 * 알약에 지난주 금요일 기록이 보였습니다.
 */
class RoutineDaysTest {
    private val seoul = ZoneId.of("Asia/Seoul")

    /** 2026-11-18은 수요일입니다. */
    private val wednesday = LocalDate.of(2026, 11, 18)

    @Test
    fun theWeekRunsFromMondayToSunday() {
        assertEquals(DayOfWeek.MONDAY, RoutineStatsCalculator.WEEK_ORDER.first())
        assertEquals(DayOfWeek.SUNDAY, RoutineStatsCalculator.WEEK_ORDER.last())
        assertEquals(LocalDate.of(2026, 11, 16), RoutineDays.weekStart(wednesday))
        // 월요일은 제 주의 첫날이고, 일요일은 앞 월요일에서 시작한 주의 끝날입니다.
        assertEquals(LocalDate.of(2026, 11, 16), RoutineDays.weekStart(LocalDate.of(2026, 11, 16)))
        assertEquals(LocalDate.of(2026, 11, 16), RoutineDays.weekStart(LocalDate.of(2026, 11, 22)))
        assertEquals(LocalDate.of(2026, 11, 23), RoutineDays.weekStart(LocalDate.of(2026, 11, 23)))
    }

    @Test
    fun eachPillPointsAtThatDayOfThisWeekEvenAfterToday() {
        val week = RoutineDays.weekOf(wednesday, 0)

        assertEquals(LocalDate.of(2026, 11, 16), RoutineDays.dateIn(week, DayOfWeek.MONDAY))
        assertEquals(wednesday, RoutineDays.dateIn(week, DayOfWeek.WEDNESDAY))
        // 오늘 뒤 요일은 이번 주의 남은 날입니다 — 지난주 13일(금)이 아니라 20일(금)입니다.
        assertEquals(LocalDate.of(2026, 11, 20), RoutineDays.dateIn(week, DayOfWeek.FRIDAY))
        assertEquals(LocalDate.of(2026, 11, 22), RoutineDays.dateIn(week, DayOfWeek.SUNDAY))
    }

    @Test
    fun theSevenPillsAreTheWeekThatHoldsTodayOnEveryDayOfTheWeek() {
        (0L..6L).map { LocalDate.of(2026, 11, 16).plusDays(it) }.forEach { today ->
            val week = RoutineDays.weekOf(today, 0)
            val pointed = RoutineStatsCalculator.WEEK_ORDER.map { RoutineDays.dateIn(week, it) }
            assertEquals("$today", (16..22).map { LocalDate.of(2026, 11, it) }, pointed)
            assertTrue("$today: 오늘이 이번 주에 없습니다.", today in pointed)
        }
    }

    @Test
    fun earlierWeeksAreWholeWeeksBack() {
        assertEquals(LocalDate.of(2026, 11, 9), RoutineDays.weekOf(wednesday, -1))
        assertEquals(LocalDate.of(2026, 10, 26), RoutineDays.weekOf(wednesday, -3))
        // 지난주 일요일은 이번 주 월요일의 어제입니다 — 월요일 아침에 어제 것을 적는 길입니다.
        val monday = LocalDate.of(2026, 11, 16)
        assertEquals(monday.minusDays(1), RoutineDays.dateIn(RoutineDays.weekOf(monday, -1), DayOfWeek.SUNDAY))
    }

    @Test
    fun theWeekIsNamedByHowFarBackItIsAndItsDates() {
        assertEquals("이번 주 · 11월 16일 ~ 22일", RoutineDays.weekLabel(RoutineDays.weekOf(wednesday, 0), wednesday))
        assertEquals("지난주 · 11월 9일 ~ 15일", RoutineDays.weekLabel(RoutineDays.weekOf(wednesday, -1), wednesday))
        // 달을 넘기면 끝날에도 달을 적습니다.
        assertEquals("3주 전 · 10월 26일 ~ 11월 1일", RoutineDays.weekLabel(RoutineDays.weekOf(wednesday, -3), wednesday))
    }

    @Test
    fun theYearIsWrittenOnlyWhenItIsNotThisYears() {
        // 2026-12-31은 목요일이고, 그 주는 2027년 1월 3일에 끝납니다.
        val newYearsEve = LocalDate.of(2026, 12, 31)
        assertEquals(
            "이번 주 · 12월 28일 ~ 2027년 1월 3일",
            RoutineDays.weekLabel(RoutineDays.weekOf(newYearsEve, 0), newYearsEve)
        )
        // 해가 바뀐 뒤 지난해에 시작한 주를 보면 앞날에도 해를 적습니다.
        val newYearsDay = LocalDate.of(2027, 1, 1)
        assertEquals(
            "이번 주 · 2026년 12월 28일 ~ 2027년 1월 3일",
            RoutineDays.weekLabel(RoutineDays.weekOf(newYearsDay, 0), newYearsDay)
        )
        assertEquals(
            "지난주 · 2026년 12월 21일 ~ 27일",
            RoutineDays.weekLabel(RoutineDays.weekOf(newYearsDay, -1), newYearsDay)
        )
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
