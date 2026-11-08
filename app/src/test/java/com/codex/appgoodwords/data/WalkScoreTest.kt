package com.codex.appgoodwords.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 걷기가 하루의 걸음·점수·연속 날수에 들어가는지(사용자가 정함, 2026-11-07 — "하루의 걸음에 걷기 넣기").
 *
 * 걸은 날은 [Walking] 한 곳에서 정합니다. 카드와 점수가 다른 날을 걸은 날이라고 말하면 안 됩니다.
 */
class WalkScoreTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 11, 7)
    private val steps = listOf(DailyStep.QUOTE, DailyStep.ROUTINE, DailyStep.WALK, DailyStep.DIARY)

    @Test
    fun walkingPastTheGoalStepsOnTheWalkStep() {
        val digest = digest(walkRecords = WalkRecords(stepCounts = listOf(count(today, 6_240)), goalSteps = 5_000))

        assertTrue(DailyStep.WALK in digest.score.doneSteps)
        assertTrue(digest.score.walked)
        assertEquals(6_240, digest.score.walkSteps)
        // 네 걸음 중 하나를 밟았습니다.
        assertEquals(20, digest.score.stepScore)
    }

    @Test
    fun walkingCountsAsOneDeedNotPerStep() {
        // 걸음 수가 아무리 많아도 덤은 하나입니다. 걸음 수로 세면 많이 걸은 날 하나로 덤이 꽉 찹니다.
        val digest = digest(walkRecords = WalkRecords(stepCounts = listOf(count(today, 25_000))))

        assertEquals(1, digest.score.deedCount)
    }

    @Test
    fun fewStepsAreNotAWalkedDayAndAreNotCountedAgainst() {
        val digest = digest(walkRecords = WalkRecords(stepCounts = listOf(count(today, 1_200)), goalSteps = 5_000))

        assertFalse(digest.score.walked)
        assertEquals(0, digest.score.deedCount)
        // 걸음 수는 보여 주지만 모자란 수를 따로 세지 않습니다.
        assertEquals(1_200, digest.score.walkSteps)
    }

    @Test
    fun aRecordedWalkShowsInTheDigestWithoutItsPath() {
        val walk = walk(today, LocalTime.of(18, 40), minutes = 32, meters = 2_100)

        val digest = digest(walkRecords = WalkRecords(walks = listOf(walk)))

        assertTrue(digest.score.walked)
        assertEquals(listOf(walk.summary()), digest.walks)
    }

    @Test
    fun theTrendCountsWalkedDays() {
        val records = WalkRecords(
            stepCounts = listOf(count(today, 6_000), count(today.minusDays(1), 100))
        )

        val trend = DayScoreCalculator.trend(
            today = today,
            events = emptyList(),
            routineChecks = emptyList(),
            todos = emptyList(),
            diaries = emptyList(),
            steps = steps,
            walkRecords = records,
            zoneId = zone
        )

        assertEquals(20, trend.points.last().score)
        assertEquals(0, trend.points[trend.points.size - 2].score)
    }

    @Test
    fun theLoopCardAndTheStreakSeeTheSameWalkedDays() {
        val records = WalkRecords(
            stepCounts = listOf(count(today, 5_500), count(today.minusDays(1), 7_000)),
            goalSteps = 5_000
        )

        val progress = DailyLoopCalculator.build(
            events = emptyList(),
            routineChecks = emptyList(),
            todos = emptyList(),
            diaries = emptyList(),
            today = today,
            steps = listOf(DailyStep.WALK),
            walkRecords = records,
            zoneId = zone
        )

        assertEquals(setOf(DailyStep.WALK), progress.doneSteps)
        assertEquals(2, progress.streakDays)
    }

    @Test
    fun choosingWalkWithoutAnyRecordsDoesNotBreakTheCard() {
        // 걷기를 골랐는데 아직 기록이 없으면 그냥 안 밟은 걸음입니다. 앱이 서면 안 됩니다.
        val progress = DailyLoopCalculator.build(
            events = emptyList(),
            routineChecks = emptyList(),
            todos = emptyList(),
            diaries = emptyList(),
            today = today,
            steps = steps,
            zoneId = zone
        )

        assertFalse(DailyStep.WALK in progress.doneSteps)
    }

    @Test
    fun walkIsAKnownStepName() {
        assertEquals(steps, DailyStep.parse(DailyStep.store(steps)))
    }

    @Test
    fun walkGoesRightBeforeTheDiary() {
        // 하루를 걷고 나서 적는 차례입니다(목업의 "글귀·루틴·걷기·일기").
        assertEquals(steps, DailyStep.withWalk(DailyStep.DEFAULTS))
        assertEquals(
            listOf(DailyStep.ROUTINE, DailyStep.TODO, DailyStep.WALK),
            DailyStep.withWalk(listOf(DailyStep.ROUTINE, DailyStep.TODO))
        )
        assertEquals("이미 있으면 그대로", listOf(DailyStep.WALK, DailyStep.QUOTE), DailyStep.withWalk(listOf(DailyStep.WALK, DailyStep.QUOTE)))
    }

    private fun digest(walkRecords: WalkRecords) = DayScoreCalculator.digest(
        date = today,
        events = emptyList(),
        routineChecks = emptyList(),
        todos = emptyList(),
        diaries = emptyList(),
        steps = steps,
        walkRecords = walkRecords,
        zoneId = zone
    )

    private fun count(date: LocalDate, steps: Int) =
        StepCountEntity(syncId = "s-$date", date = date.toString(), steps = steps, updatedAt = 1L, createdAt = 1L)

    private fun walk(date: LocalDate, start: LocalTime, minutes: Long, meters: Int): WalkEntity {
        val startedAt = date.atTime(start).atZone(zone).toInstant().toEpochMilli()
        return WalkEntity(
            syncId = "w-$date",
            startedAt = startedAt,
            endedAt = startedAt + minutes * 60_000L,
            distanceMeters = meters,
            path = WalkPath.encode(listOf(GeoPoint(37.5665, 126.978), GeoPoint(37.57, 126.98)))
        )
    }
}
