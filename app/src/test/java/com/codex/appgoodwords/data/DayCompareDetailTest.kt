package com.codex.appgoodwords.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 어제와 오늘 화면의 칸(사용자가 고름, 2026-11-27 — 항목 카드 두 칸). 왼쪽 어제 하루, 오른쪽 오늘 지금까지, 자세한 것은 늘
 * 펼쳐 둡니다. 기분은 견주지 않고 나란히만 둡니다.
 */
class DayCompareDetailTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 11, 27)
    private val yesterday = today.minusDays(1)

    @Test
    fun cardsCoverOnlySectionsEitherDayHasInOrder() {
        val cards = DayCompareDetails.cards(
            DayPair(
                today = day(today, routines = mapOf("물 2L" to 2, "독서 20분" to 1), quotes = listOf("작은 일을 꾸준히"), steps = 7_010),
                yesterday = day(yesterday, routines = mapOf("물 2L" to 1), todos = listOf("장보기"), steps = 6_240)
            ),
            zoneId = zone
        )

        assertEquals(
            listOf(CompareSection.ROUTINE, CompareSection.QUOTE, CompareSection.STEPS, CompareSection.TODO),
            cards.map { it.section }
        )
        val routine = cards.first()
        assertEquals("어제 1번 · 오늘 3번", routine.summary)
        assertEquals("+2", routine.gain)
        assertEquals("3번", routine.today.headline)
        // 같은 루틴을 두 번 밟았으면 한 줄에 ×2입니다.
        assertEquals(listOf(CompareLine("✓", "물 2L", "×2"), CompareLine("✓", "독서 20분")), routine.today.lines)
        assertEquals(listOf(CompareLine("✓", "물 2L")), routine.yesterday.lines)

        val steps = cards.first { it.section == CompareSection.STEPS }
        assertEquals("어제 6,240 · 오늘 7,010", steps.summary)
        assertEquals("+770", steps.gain)
        assertEquals("7,010걸음", steps.today.headline)

        // 오늘 적은 칸은 수만 나란히 두고 빛 칩은 없습니다. 비어 있는 쪽은 화면이 "아직 없음"으로 적습니다.
        val todo = cards.first { it.section == CompareSection.TODO }
        assertEquals("", todo.gain)
        assertTrue(todo.today.isEmpty)
        assertEquals(listOf(CompareLine("✓", "장보기")), todo.yesterday.lines)
    }

    @Test
    fun moodSitsSideBySideWithTimesAndIsNeverCompared() {
        val cards = DayCompareDetails.cards(
            DayPair(
                today = day(today, moods = listOf(DiaryMood.GOOD to "09:00", DiaryMood.GREAT to "12:40")),
                yesterday = day(yesterday, moods = listOf(DiaryMood.GREAT to "08:10"))
            ),
            zoneId = zone
        )

        val mood = cards.single()
        assertEquals(CompareSection.MOOD, mood.section)
        assertEquals("견주지 않고 나란히", mood.summary)
        assertEquals("", mood.gain)
        assertEquals("", mood.today.headline)
        assertEquals(
            listOf(CompareLine(DiaryMood.GOOD.emoji, DiaryMood.GOOD.label, "09:00"), CompareLine(DiaryMood.GREAT.emoji, DiaryMood.GREAT.label, "12:40")),
            mood.today.lines
        )
    }

    @Test
    fun aDiaryOnlyMoodShowsOnceAsDiary() {
        val cards = DayCompareDetails.cards(
            DayPair(today = day(today), yesterday = day(yesterday, diaryMood = DiaryMood.TIRED)),
            zoneId = zone
        )

        assertEquals(listOf(CompareLine(DiaryMood.TIRED.emoji, DiaryMood.TIRED.label, "일기")), cards.single().yesterday.lines)
    }

    @Test
    fun lockedDiariesShowOnlyHowMany() {
        val pair = DayPair(today = day(today, diaries = listOf("비 오던 날")), yesterday = day(yesterday))

        assertEquals(listOf(CompareLine("✎", "비 오던 날")), DayCompareDetails.cards(pair, zoneId = zone).single().today.lines)
        assertEquals(
            listOf(CompareLine("🔒", "잠긴 일기 1편")),
            DayCompareDetails.cards(pair, diaryLocked = true, zoneId = zone).single().today.lines
        )
    }

    @Test
    fun walksWithoutStepCountsSpeakInWalks() {
        val cards = DayCompareDetails.cards(
            DayPair(
                today = day(today, walks = listOf(walk("07:30", meters = 2_140, minutes = 32))),
                yesterday = day(yesterday)
            ),
            zoneId = zone
        )

        val steps = cards.single()
        assertEquals("걷기 어제 0번 · 오늘 1번", steps.summary)
        assertEquals(listOf(CompareLine("🚶", "2.1km · 32분", "07:30")), steps.today.lines)
    }

    @Test
    fun theDigestCountsEachRoutineInTheOrderItWasFirstChecked() {
        val at = { hhmm: String -> today.atTime(java.time.LocalTime.parse(hhmm)).atZone(zone).toInstant().toEpochMilli() }
        val digest = DayScoreCalculator.digest(
            date = today,
            events = emptyList(),
            routineChecks = listOf(
                RoutineCheckEntity(routineId = 2, routineTitle = "물 2L", checkedAt = at("13:00")),
                RoutineCheckEntity(routineId = 1, routineTitle = "스트레칭", checkedAt = at("07:00")),
                RoutineCheckEntity(routineId = 2, routineTitle = "물 2L", checkedAt = at("09:00"))
            ),
            todos = emptyList(),
            diaries = emptyList(),
            zoneId = zone
        )

        assertEquals(listOf("스트레칭" to 1, "물 2L" to 2), digest.routineCounts.toList())
    }

    private fun walk(start: String, meters: Int, minutes: Long): WalkSummary {
        val startedAt = today.atTime(java.time.LocalTime.parse(start)).atZone(zone).toInstant().toEpochMilli()
        return WalkSummary(syncId = "walk-$start", startedAt = startedAt, endedAt = startedAt + minutes * 60_000, distanceMeters = meters, steps = 0)
    }

    private fun day(
        date: LocalDate,
        routines: Map<String, Int> = emptyMap(),
        quotes: List<String> = emptyList(),
        todos: List<String> = emptyList(),
        diaries: List<String> = emptyList(),
        steps: Int? = null,
        walks: List<WalkSummary> = emptyList(),
        moods: List<Pair<DiaryMood, String>> = emptyList(),
        diaryMood: DiaryMood? = null
    ) = DayDigest(
        score = DayScore(
            date = date,
            steps = DailyStep.DEFAULTS,
            doneSteps = emptySet(),
            routineChecks = routines.values.sum(),
            todosDone = todos.size,
            diaries = diaries.size,
            quotesRead = quotes.size,
            walkSteps = steps,
            mood = diaryMood
        ),
        routineTitles = routines.keys.toList(),
        todoTitles = todos,
        diaryTitles = diaries,
        quoteTitles = quotes,
        moodLogs = moods.map { (mood, hhmm) ->
            MoodLogEntity(
                entryDate = date.toString(),
                mood = mood.name,
                createdAt = date.atTime(java.time.LocalTime.parse(hhmm)).atZone(zone).toInstant().toEpochMilli()
            )
        },
        walks = walks,
        routineCounts = routines
    )
}
