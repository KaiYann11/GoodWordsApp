package com.codex.appgoodwords.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 얼마나 뜸했는지를 세는 규칙입니다.
 *
 * 화면에 적는 날수와 알림이 말하는 날수가 여기서 함께 나옵니다. 갈라지면 "3일 전"이라고
 * 적힌 화면을 보면서 "7일째 안 했다"는 알림을 받게 됩니다.
 */
class GrowthCadenceTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 9, 1)

    @Test
    fun withoutAnyReportThereIsNothingToCount() {
        assertNull(GrowthCadence.daysSince(emptyList(), at(today), zone))
        assertNull(GrowthCadence.lastReviewedAt(emptyList()))
    }

    @Test
    fun neverReviewedIsNotNagged() {
        // 앱을 막 깐 사람에게 첫날부터 "안 했다"고 알리면 그저 성가십니다.
        assertFalse(GrowthCadence.isOverdue(emptyList(), at(today), zoneId = zone))
    }

    @Test
    fun theNewestReportWins() {
        val reports = listOf(
            report(today.minusDays(10)),
            report(today.minusDays(2)),
            report(today.minusDays(30))
        )

        assertEquals(2L, GrowthCadence.daysSince(reports, at(today), zone))
    }

    @Test
    fun daysAreCountedByDateNotByHours() {
        // 어젯밤 늦게 돌렸으면 스물몇 시간이 안 지났어도 "어제"입니다.
        val lastNight = at(today.minusDays(1), LocalTime.of(23, 30))
        val thisMorning = at(today, LocalTime.of(8, 0))

        assertEquals(1L, GrowthCadence.daysSince(listOf(report(lastNight)), thisMorning, zone))
    }

    @Test
    fun aWeekIsTheLine() {
        val sixDays = listOf(report(today.minusDays(6)))
        val sevenDays = listOf(report(today.minusDays(7)))

        assertFalse(GrowthCadence.isOverdue(sixDays, at(today), zoneId = zone))
        assertTrue(GrowthCadence.isOverdue(sevenDays, at(today), zoneId = zone))
    }

    @Test
    fun aClockSkewDoesNotProduceNegativeDays() {
        // "-2일 전"은 읽을 수 없습니다.
        val fromTheFuture = listOf(report(today.plusDays(2)))

        assertEquals(0L, GrowthCadence.daysSince(fromTheFuture, at(today), zone))
        assertFalse(GrowthCadence.isOverdue(fromTheFuture, at(today), zoneId = zone))
    }

    @Test
    fun theFirstNudgeAlwaysGoesOut() {
        assertTrue(GrowthCadence.shouldNudgeAgain(lastNudgedAt = 0L, now = at(today), zoneId = zone))
    }

    @Test
    fun itDoesNotNagEveryDay() {
        // 뜸해진 뒤로 매일 알리면 잔소리가 됩니다.
        val yesterday = at(today.minusDays(1))

        assertFalse(GrowthCadence.shouldNudgeAgain(yesterday, at(today), zoneId = zone))
    }

    @Test
    fun afterAWeekItSpeaksAgain() {
        val aWeekAgo = at(today.minusDays(7))

        assertTrue(GrowthCadence.shouldNudgeAgain(aWeekAgo, at(today), zoneId = zone))
    }

    private fun at(date: LocalDate, time: LocalTime = LocalTime.NOON): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    private fun report(date: LocalDate) = report(at(date))

    private fun report(createdAt: Long) = GrowthReportEntity(
        syncId = "r-$createdAt",
        period = ReportPeriod.WEEKLY.name,
        createdAt = createdAt
    )
}
