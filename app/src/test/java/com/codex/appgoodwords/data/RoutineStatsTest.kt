package com.codex.appgoodwords.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 루틴 하나를 지금까지 어떻게 밟아 왔는지.
 *
 * 여기서 특히 보는 것은 **횟수와 날수를 섞지 않는지**입니다. 하루에 세 번 눌렀다고 사흘이
 * 되면 연속 날수와 요일 통계가 통째로 거짓말을 합니다. 그리고 **없는 것을 지어내지 않는지** —
 * 한 번뿐인 요일을 "가장 많이 한 요일"이라 부르면 처음 밟은 날이 곧 내 요일이 됩니다.
 */
class RoutineStatsTest {
    private val zone = ZoneId.of("Asia/Seoul")

    /** 2026-10-16은 금요일입니다. */
    private val today = LocalDate.of(2026, 10, 16)

    private val routine = RoutineEntity(id = 1, syncId = "routine-1", title = "아침 산책")
    private val other = RoutineEntity(id = 2, syncId = "routine-2", title = "스트레칭")

    private fun check(
        date: String,
        hour: Int = 9,
        routineSyncId: String = "routine-1",
        routineId: Long = 1
    ) = RoutineCheckEntity(
        syncId = "check-$routineId-$date-$hour",
        routineId = routineId,
        routineSyncId = routineSyncId,
        routineTitle = "아침 산책",
        checkedAt = LocalDate.parse(date).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    )

    private fun build(checks: List<RoutineCheckEntity>) =
        RoutineStatsCalculator.build(routine, checks, today, zone)

    @Test fun threeChecksInOneDayAreStillOneDay() {
        // 횟수는 횟수대로 세되, 이어 온 날수는 날로 셉니다.
        val report = build(
            listOf(check("2026-10-16", hour = 7), check("2026-10-16", hour = 13), check("2026-10-16", hour = 21))
        )

        assertEquals(3, report.totalChecks)
        assertEquals(1, report.doneDays)
        assertEquals(1, report.currentStreakDays)
    }

    @Test fun theRunSurvivesAMorningNotSteppedYet() {
        // 아침에 열었을 때 0일로 보이면 어제까지 쌓은 것이 사라진 것처럼 느껴집니다.
        val report = build(listOf(check("2026-10-14"), check("2026-10-15")))

        assertEquals(2, report.currentStreakDays)
    }

    @Test fun aMissedDayEndsTheRunButTheBestRemembersIt() {
        val report = build(
            listOf(check("2026-10-10"), check("2026-10-11"), check("2026-10-12"), check("2026-10-16"))
        )

        assertEquals(1, report.currentStreakDays)
        assertEquals(3, report.bestStreakDays)
    }

    @Test fun aCheckThatPointsAtAnotherRoutineDoesNotCount() {
        // 기기 간 식별자는 syncId뿐입니다. 숫자 id가 같다고 같은 루틴이 아닙니다.
        val report = build(
            listOf(check("2026-10-16"), check("2026-10-15", routineSyncId = "routine-2", routineId = 1))
        )

        assertEquals(1, report.totalChecks)
        assertEquals(LocalDate.of(2026, 10, 16), report.firstDoneDate)
    }

    @Test fun anOldCheckWithoutASyncIdStillFindsItsRoutine() {
        // 아직 한 번도 동기화하지 않은 기기에는 syncId가 비어 있는 옛 체크가 있습니다.
        val report = build(listOf(check("2026-10-16", routineSyncId = "")))

        assertEquals(1, report.totalChecks)
    }

    @Test fun theWeekdayCountsDaysNotChecks() {
        // 10월 5일과 12일이 월요일입니다. 12일에 두 번 눌러도 월요일은 두 날입니다.
        val report = build(
            listOf(check("2026-10-05"), check("2026-10-12", hour = 7), check("2026-10-12", hour = 20))
        )

        assertEquals(2, report.weekdays.single { it.dayOfWeek == DayOfWeek.MONDAY }.days)
        assertEquals(3, report.totalChecks)
        assertEquals(DayOfWeek.MONDAY, report.topWeekday)
    }

    @Test fun theWeekRunsFromSunday() {
        // 달력 격자와 차례가 같아야 같은 요일을 두 화면에서 다른 자리로 보지 않습니다.
        assertEquals(DayOfWeek.SUNDAY, build(emptyList()).weekdays.first().dayOfWeek)
        assertEquals(DayOfWeek.SATURDAY, build(emptyList()).weekdays.last().dayOfWeek)
        assertEquals(7, build(emptyList()).weekdays.size)
    }

    @Test fun aTieIsNotCalledTheTopWeekday() {
        // 월·화가 나란히 둘이면 그중 하나를 집어 "월요일에 많이 한다"고 말할 수 없습니다.
        val report = build(
            listOf(check("2026-10-05"), check("2026-10-12"), check("2026-10-06"), check("2026-10-13"))
        )

        assertNull(report.topWeekday)
        assertEquals(2, report.busiestWeekdayDays)
    }

    @Test fun aWeekdaySteppedOnlyOnceIsNotCalledTheTop() {
        // 한 번뿐인 요일을 으뜸이라 부르면 처음 밟은 날이 그대로 "내 요일"이 됩니다.
        val report = build(listOf(check("2026-10-12")))

        assertNull(report.topWeekday)
        assertEquals(1, report.doneDays)
    }

    @Test fun theFirstAndLastDayComeFromTheRecords() {
        val report = build(listOf(check("2026-10-16"), check("2026-10-01"), check("2026-10-09")))

        assertEquals(LocalDate.of(2026, 10, 1), report.firstDoneDate)
        assertEquals(LocalDate.of(2026, 10, 16), report.lastDoneDate)
        assertEquals(setOf(
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 9),
            LocalDate.of(2026, 10, 16)
        ), report.doneDates)
    }

    @Test fun everyRoutineGetsAReportEvenWhenItWasNeverStepped() {
        // 화면이 빈 칸에서 null을 다시 다루지 않도록 루틴마다 한 칸씩 둡니다.
        val all = RoutineStatsCalculator.buildAll(listOf(routine, other), emptyList(), today, zone)

        assertEquals(setOf(1L, 2L), all.keys)
        assertTrue(all.getValue(2L).isEmpty)
        assertEquals(0, all.getValue(2L).currentStreakDays)
        assertNull(all.getValue(2L).lastDoneDate)
    }

    @Test fun eachRoutineKeepsItsOwnRun() {
        val checks = listOf(
            check("2026-10-15"),
            check("2026-10-16"),
            check("2026-10-16", routineSyncId = "routine-2", routineId = 2)
        )

        val all = RoutineStatsCalculator.buildAll(listOf(routine, other), checks, today, zone)

        assertEquals(2, all.getValue(1L).currentStreakDays)
        assertEquals(1, all.getValue(2L).currentStreakDays)
    }
}
