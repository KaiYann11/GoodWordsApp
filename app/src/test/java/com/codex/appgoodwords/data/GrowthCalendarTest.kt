package com.codex.appgoodwords.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 돌아보기를 달력에 놓는 규칙입니다.
 *
 * **돌린 날과 다뤄진 날은 다릅니다.** 지난 한 주를 오늘 돌아봤다면 오늘은 돌린 날이고
 * 지난 이레는 다뤄진 날입니다. 둘을 섞으면 "언제 돌아봤나"와 "어디까지 봤나"를 가릴 수 없습니다.
 */
class GrowthCalendarTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val september = YearMonth.of(2026, 9)

    @Test
    fun aMonthHasOneCellPerDay() {
        val month = GrowthCalendar.month(emptyList(), september, zone)

        assertEquals(30, month.days.size)
        assertEquals(LocalDate.of(2026, 9, 1), month.days.first().date)
        assertEquals(LocalDate.of(2026, 9, 30), month.days.last().date)
    }

    @Test
    fun withoutAnyReportEveryDayIsUntouched() {
        val month = GrowthCalendar.month(emptyList(), september, zone)

        assertEquals(30, month.untouchedDays)
        assertEquals(0, month.ranDays)
        assertEquals(0, month.coveredDays)
    }

    @Test
    fun theDayItWasRunIsMarkedApartFromTheDaysItCovered() {
        // 9월 8일에 돌리면서 9월 1일~7일을 다뤘습니다.
        val report = report(
            createdAt = at(LocalDate.of(2026, 9, 8)),
            start = "2026-09-01",
            end = "2026-09-07"
        )

        val month = GrowthCalendar.month(listOf(report), september, zone)
        val ranDay = month.days.first { it.date == LocalDate.of(2026, 9, 8) }
        val coveredDay = month.days.first { it.date == LocalDate.of(2026, 9, 3) }

        assertTrue("돌린 날이 표시되지 않았습니다.", ranDay.ran)
        assertFalse("돌린 날이 기간에도 든 것으로 잘못 셈했습니다.", ranDay.covered)
        assertTrue(coveredDay.covered)
        assertFalse(coveredDay.ran)
    }

    @Test
    fun bothEndsOfThePeriodAreIncluded() {
        val report = report(
            createdAt = at(LocalDate.of(2026, 9, 8)),
            start = "2026-09-01",
            end = "2026-09-07"
        )

        val month = GrowthCalendar.month(listOf(report), september, zone)

        // 끝날을 빼면 이레짜리가 엿새로 보입니다.
        assertTrue(month.days.first { it.date == LocalDate.of(2026, 9, 1) }.covered)
        assertTrue(month.days.first { it.date == LocalDate.of(2026, 9, 7) }.covered)
        assertEquals(7, month.coveredDays)
    }

    @Test
    fun aDayCanBeBothRunAndCovered() {
        // 오늘까지를 오늘 돌아본 경우입니다.
        val report = report(
            createdAt = at(LocalDate.of(2026, 9, 7)),
            start = "2026-09-01",
            end = "2026-09-07"
        )

        val day = GrowthCalendar.month(listOf(report), september, zone)
            .days.first { it.date == LocalDate.of(2026, 9, 7) }

        assertTrue(day.ran)
        assertTrue(day.covered)
        assertFalse(day.untouched)
    }

    @Test
    fun overlappingReportsAreCounted() {
        // 같은 구간을 두 번 돌아볼 수 있습니다. 몇 편이 다뤘는지가 보여야 합니다.
        val reports = listOf(
            report(at(LocalDate.of(2026, 9, 8)), "2026-09-01", "2026-09-07"),
            report(at(LocalDate.of(2026, 9, 9)), "2026-09-05", "2026-09-09")
        )

        val day = GrowthCalendar.month(reports, september, zone)
            .days.first { it.date == LocalDate.of(2026, 9, 6) }

        assertEquals(2, day.coveredCount)
    }

    @Test
    fun theGapsAreWhatThisIsFor() {
        // 빠뜨린 구간이 눈에 보여야 다음에 무엇을 돌아볼지 정할 수 있습니다.
        val report = report(at(LocalDate.of(2026, 9, 8)), "2026-09-01", "2026-09-07")

        val month = GrowthCalendar.month(listOf(report), september, zone)

        // 1~7 다뤄짐, 8 돌림. 나머지 스물둘이 빈 자리입니다.
        assertEquals(22, month.untouchedDays)
    }

    @Test
    fun aReversedPeriodIsStillRead() {
        // 손으로 붙여 넣은 답에서 시작과 끝이 뒤집혀 들어올 수 있습니다.
        val report = report(at(LocalDate.of(2026, 9, 8)), start = "2026-09-07", end = "2026-09-01")

        val month = GrowthCalendar.month(listOf(report), september, zone)

        assertEquals(7, month.coveredDays)
    }

    @Test
    fun aReportWithoutAPeriodOnlyMarksTheDayItWasRun() {
        val report = report(at(LocalDate.of(2026, 9, 8)), start = "", end = "")

        val month = GrowthCalendar.month(listOf(report), september, zone)

        assertEquals(1, month.ranDays)
        assertEquals(0, month.coveredDays)
    }

    @Test
    fun anotherMonthIsNotTouched() {
        val report = report(at(LocalDate.of(2026, 8, 30)), "2026-08-24", "2026-08-30")

        val month = GrowthCalendar.month(listOf(report), september, zone)

        assertEquals(30, month.untouchedDays)
    }

    @Test
    fun aDayKnowsWhichReportsBelongToIt() {
        val ranHere = report(at(LocalDate.of(2026, 9, 8)), "2026-09-01", "2026-09-07")
        val coversHere = report(at(LocalDate.of(2026, 9, 20)), "2026-09-08", "2026-09-14")
        val unrelated = report(at(LocalDate.of(2026, 9, 30)), "2026-09-21", "2026-09-27")

        val found = GrowthCalendar.reportsOn(
            reports = listOf(ranHere, coversHere, unrelated),
            date = LocalDate.of(2026, 9, 8),
            zoneId = zone
        )

        // 그날 돌린 것과 그날을 다룬 것이 함께 나와야 합니다.
        assertEquals(2, found.size)
        assertTrue(found.contains(ranHere))
        assertTrue(found.contains(coversHere))
    }

    private fun at(date: LocalDate, time: LocalTime = LocalTime.NOON): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    private fun report(createdAt: Long, start: String, end: String) = GrowthReportEntity(
        syncId = "r-$createdAt-$start",
        period = ReportPeriod.WEEKLY.name,
        periodStart = start,
        periodEnd = end,
        guide = "돌아본 글",
        createdAt = createdAt
    )
}
