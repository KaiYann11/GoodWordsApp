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
 * 요일 줄은 오늘을 가운데 둔 이레입니다(사용자가 정함, 2026-11-23). 오늘 앞 사흘은 지난날이고 뒤 사흘은 아직 오지 않은
 * 날입니다. **오늘 뒤 요일이 지난주 날로 미끄러지지 않아야** 합니다 — 1.25.0까지는 일요일에 열면 그 뒤 월~토 알약이 지난주
 * 날을 가리켰고, 1.25.1(월~일 한 주)은 일요일에 일곱 칸이 모두 지난날이었습니다.
 */
class RoutineDaysTest {
    private val seoul = ZoneId.of("Asia/Seoul")

    /** 2026-11-18은 수요일입니다. */
    private val wednesday = LocalDate.of(2026, 11, 18)

    /** 2026-11-22는 일요일입니다 — 사용자가 "일요일에 열어보면" 하고 말한 날입니다. */
    private val sunday = LocalDate.of(2026, 11, 22)

    @Test
    fun theRowIsThreeDaysBackTodayAndThreeDaysAhead() {
        val start = RoutineDays.rowStart(sunday, 0)

        assertEquals(LocalDate.of(2026, 11, 19), start)
        assertEquals((19..25).map { LocalDate.of(2026, 11, it) }, RoutineDays.rowDays(start))
    }

    @Test
    fun onSundayTheDaysAfterAreTheComingOnesNotLastWeeks() {
        val start = RoutineDays.rowStart(sunday, 0)

        // 오늘 앞 사흘은 지난날입니다.
        assertEquals(LocalDate.of(2026, 11, 19), RoutineDays.dateIn(start, DayOfWeek.THURSDAY))
        assertEquals(LocalDate.of(2026, 11, 21), RoutineDays.dateIn(start, DayOfWeek.SATURDAY))
        assertEquals(sunday, RoutineDays.dateIn(start, DayOfWeek.SUNDAY))
        // 월요일은 엿새 전 16일이 아니라 내일 23일입니다.
        assertEquals(LocalDate.of(2026, 11, 23), RoutineDays.dateIn(start, DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 11, 25), RoutineDays.dateIn(start, DayOfWeek.WEDNESDAY))
    }

    @Test
    fun onEveryDayTheSevenPillsAreEachWeekdayOnceWithTodayInTheMiddle() {
        (0L..6L).map { LocalDate.of(2026, 11, 16).plusDays(it) }.forEach { today ->
            val start = RoutineDays.rowStart(today, 0)
            val days = RoutineDays.rowDays(start)

            assertEquals("$today", (-3L..3L).map { today.plusDays(it) }, days)
            assertEquals("$today", DayOfWeek.values().toSet(), days.map { it.dayOfWeek }.toSet())
            days.forEach { assertEquals("$today $it", it, RoutineDays.dateIn(start, it.dayOfWeek)) }
        }
    }

    @Test
    fun earlierRowsAreWholeWeeksBackAndKeepTheWeekday() {
        assertEquals(LocalDate.of(2026, 11, 12), RoutineDays.rowStart(sunday, -1))
        assertEquals(LocalDate.of(2026, 10, 29), RoutineDays.rowStart(sunday, -3))
        // 이레 앞 줄의 월요일은 지난 월요일(16일)입니다 — 같은 요일이 이레 앞으로 갑니다.
        assertEquals(LocalDate.of(2026, 11, 16), RoutineDays.dateIn(RoutineDays.rowStart(sunday, -1), DayOfWeek.MONDAY))
        // 이레씩 넘겨도 요일 차례는 같습니다.
        assertEquals(
            RoutineDays.rowDays(RoutineDays.rowStart(sunday, 0)).map { it.dayOfWeek },
            RoutineDays.rowDays(RoutineDays.rowStart(sunday, -2)).map { it.dayOfWeek }
        )
    }

    @Test
    fun theRowIsNamedByHowFarBackItIsAndItsDates() {
        assertEquals("오늘 앞뒤 · 11월 19일 ~ 25일", RoutineDays.rowLabel(RoutineDays.rowStart(sunday, 0), sunday))
        assertEquals("1주 전 · 11월 12일 ~ 18일", RoutineDays.rowLabel(RoutineDays.rowStart(sunday, -1), sunday))
        // 달을 넘기면 끝날에도 달을 적습니다.
        assertEquals("3주 전 · 10월 29일 ~ 11월 4일", RoutineDays.rowLabel(RoutineDays.rowStart(sunday, -3), sunday))
    }

    @Test
    fun theYearIsWrittenOnlyWhenItIsNotThisYears() {
        // 2026-12-31의 줄은 12월 28일에서 2027년 1월 3일까지입니다.
        val newYearsEve = LocalDate.of(2026, 12, 31)
        assertEquals(
            "오늘 앞뒤 · 12월 28일 ~ 2027년 1월 3일",
            RoutineDays.rowLabel(RoutineDays.rowStart(newYearsEve, 0), newYearsEve)
        )
        // 해가 바뀐 뒤 지난해에 시작한 줄을 보면 앞날에도 해를 적습니다.
        val newYearsDay = LocalDate.of(2027, 1, 1)
        assertEquals(
            "오늘 앞뒤 · 2026년 12월 29일 ~ 2027년 1월 4일",
            RoutineDays.rowLabel(RoutineDays.rowStart(newYearsDay, 0), newYearsDay)
        )
        assertEquals(
            "1주 전 · 2026년 12월 22일 ~ 28일",
            RoutineDays.rowLabel(RoutineDays.rowStart(newYearsDay, -1), newYearsDay)
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
