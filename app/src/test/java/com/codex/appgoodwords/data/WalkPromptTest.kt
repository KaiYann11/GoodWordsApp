package com.codex.appgoodwords.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI(오늘의 한마디·돌아보기)에 걷기는 **숫자만** 가는지(사용자가 정함, 2026-11-07 — 위치는 AI에 넘기지 않음).
 *
 * 지나온 길·좌표가 한 글자라도 섞이면 안 됩니다. 미리 보기와 실제로 보내는 글이 같은 함수라 여기서 막으면 둘 다 막힙니다.
 */
class WalkPromptTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 11, 7)
    private val route = listOf(GeoPoint(37.5665, 126.978), GeoPoint(37.5759, 126.9768))
    private val walk = run {
        val startedAt = today.atTime(18, 40).atZone(zone).toInstant().toEpochMilli()
        WalkEntity(
            syncId = "w1",
            startedAt = startedAt,
            endedAt = startedAt + 32 * 60_000L,
            distanceMeters = 2_100,
            steps = 2_900,
            path = WalkPath.encode(route)
        )
    }

    @Test
    fun theCoachSeesTheWalkAsNumbers() {
        val prompt = DailyCoachPrompt.userPrompt(coachDigest(WalkRecords(stepCounts = listOf(count(6_240)), walks = listOf(walk))))

        assertTrue(prompt, prompt.contains("걷기: 6,240걸음 · 기록 2.1km 32분"))
        assertNoLocation(prompt)
    }

    @Test
    fun theCoachDoesNotCountFewSteps() {
        // 아침의 몇백 걸음만 보고 말을 걸면 모자란 걸음을 세는 셈입니다. 걸은 날이 된 뒤에만 싣습니다.
        val digest = coachDigest(WalkRecords(stepCounts = listOf(count(1_200))))

        assertFalse(DailyCoachPrompt.userPrompt(digest).contains("걷기:"))
        assertTrue("걸음 수만으로는 물을 거리가 생기지 않습니다", digest.isEmpty)
    }

    @Test
    fun theReviewListsWalkingDaysAsNumbers() {
        val digest = GrowthPrompt.digest(
            period = ReportPeriod.WEEKLY,
            today = today,
            routines = emptyList(),
            routineChecks = emptyList(),
            todos = emptyList(),
            diaries = emptyList(),
            items = emptyList(),
            events = emptyList(),
            books = emptyList(),
            includeDiaryBody = false,
            walkRecords = WalkRecords(
                stepCounts = listOf(count(6_240), count(3_100, today.minusDays(2))),
                walks = listOf(walk)
            ),
            zoneId = zone
        )
        val prompt = GrowthPrompt.userPrompt(digest)

        assertFalse("걷기만 있어도 돌아볼 것이 있습니다", digest.isEmpty)
        assertTrue(prompt, prompt.contains("- 2026-11-07 6,240걸음 · 기록 2.1km 32분 (걸은 날)"))
        assertTrue(prompt, prompt.contains("- 2026-11-05 3,100걸음"))
        assertFalse("못 걸은 날을 걸은 날로 적었습니다", prompt.contains("2026-11-05 3,100걸음 (걸은 날)"))
        assertNoLocation(prompt)
    }

    private fun assertNoLocation(prompt: String) {
        assertFalse("지나온 길이 실렸습니다", prompt.contains(walk.path))
        route.forEach { point ->
            assertFalse("위도가 실렸습니다", prompt.contains(point.lat.toString().take(6)))
            assertFalse("경도가 실렸습니다", prompt.contains(point.lng.toString().take(6)))
        }
    }

    private fun coachDigest(records: WalkRecords): DailyCoachDigest {
        val steps = listOf(DailyStep.QUOTE, DailyStep.ROUTINE, DailyStep.WALK, DailyStep.DIARY)
        return DailyCoachPrompt.digest(
            today = today,
            at = LocalTime.of(21, 0),
            dayDigest = DayScoreCalculator.digest(
                date = today,
                events = emptyList(),
                routineChecks = emptyList(),
                todos = emptyList(),
                diaries = emptyList(),
                steps = steps,
                walkRecords = records,
                zoneId = zone
            ),
            progress = DailyLoopCalculator.build(
                events = emptyList(),
                routineChecks = emptyList(),
                todos = emptyList(),
                diaries = emptyList(),
                today = today,
                steps = steps,
                walkRecords = records,
                zoneId = zone
            ),
            routines = emptyList(),
            todayChecks = emptyList(),
            diaries = emptyList(),
            items = emptyList(),
            includeDiaryBody = false,
            zoneId = zone
        )
    }

    private fun count(steps: Int, date: LocalDate = today) =
        StepCountEntity(syncId = "s-$date", date = date.toString(), steps = steps, updatedAt = 1L, createdAt = 1L)
}
