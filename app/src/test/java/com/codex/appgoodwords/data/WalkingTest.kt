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
 * 걸은 날을 정하는 한 곳([Walking])의 규칙.
 *
 * 걸음 수로만 세면 휴대폰을 두고 걸은 날이 빠지고, 기록으로만 세면 출퇴근으로 많이 걸은 날이 빠집니다(사용자가 정함,
 * 2026-11-07 — 정한 걸음 수를 넘었거나 걷기를 기록한 날).
 */
class WalkingTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 11, 7)

    @Test
    fun aDayOverTheGoalIsAWalkedDay() {
        val records = WalkRecords(
            stepCounts = listOf(count(today, 5_200), count(today.minusDays(1), 4_999)),
            goalSteps = 5_000
        )

        assertEquals(setOf(today), Walking.walkedDays(records, zone))
    }

    @Test
    fun aRecordedWalkCountsEvenWithFewSteps() {
        // 휴대폰을 주머니에 두지 않아 걸음이 적게 잡힌 날도 걷기를 기록했으면 걸은 날입니다.
        val records = WalkRecords(
            stepCounts = listOf(count(today, 1_200)),
            walks = listOf(walk(today, LocalTime.of(18, 40), minutes = 32, meters = 2_100))
        )

        assertTrue(Walking.walkedOn(records, today, zone))
    }

    @Test
    fun aWalkStillInProgressDoesNotCountYet() {
        val ongoing = walk(today, LocalTime.of(7, 0), minutes = 0, meters = 300).copy(endedAt = 0L)

        assertFalse(Walking.walkedOn(WalkRecords(walks = listOf(ongoing)), today, zone))
        assertEquals(emptyList<WalkSummary>(), Walking.walksOn(WalkRecords(walks = listOf(ongoing)), today, zone))
    }

    @Test
    fun theSameDayReadTwiceKeepsTheLargerCount() {
        // 두 기기가 같은 하루를 각자 읽었습니다. 더하면 겹쳐 셉니다.
        val records = WalkRecords(stepCounts = listOf(count(today, 6_240, "a"), count(today, 300, "b")))

        assertEquals(6_240, Walking.stepsByDate(records)[today])
    }

    @Test
    fun theFactLineHoldsNumbersOnly() {
        val line = Walking.factLine(
            steps = 6_240,
            walks = listOf(walk(today, LocalTime.of(18, 40), minutes = 32, meters = 2_100).summary())
        )

        assertEquals("6,240걸음 · 기록 2.1km 32분", line)
        assertNull(Walking.factLine(steps = null, walks = emptyList()))
        assertNull("0걸음은 말하지 않습니다", Walking.factLine(steps = 0, walks = emptyList()))
    }

    @Test
    fun numbersReadNaturally() {
        assertEquals("850m", Walking.formatDistance(850))
        assertEquals("1.0km", Walking.formatDistance(1_000))
        assertEquals("12.3km", Walking.formatDistance(12_340))
        assertEquals("1분", Walking.formatDuration(20_000L))
        assertEquals("59분", Walking.formatDuration(59 * 60_000L))
        assertEquals("1시간", Walking.formatDuration(60 * 60_000L))
        assertEquals("1시간 5분", Walking.formatDuration(65 * 60_000L))
        assertEquals("10,000걸음", Walking.formatSteps(10_000))
    }

    private fun count(date: LocalDate, steps: Int, syncId: String = "s-$date") =
        StepCountEntity(syncId = syncId, date = date.toString(), steps = steps, updatedAt = 1L, createdAt = 1L)

    private fun walk(date: LocalDate, start: LocalTime, minutes: Long, meters: Int): WalkEntity {
        val startedAt = date.atTime(start).atZone(zone).toInstant().toEpochMilli()
        return WalkEntity(
            syncId = "w-$date-$start",
            startedAt = startedAt,
            endedAt = startedAt + minutes * 60_000L + 1L,
            distanceMeters = meters,
            path = WalkPath.encode(listOf(GeoPoint(37.5665, 126.978), GeoPoint(37.57, 126.98)))
        )
    }
}
