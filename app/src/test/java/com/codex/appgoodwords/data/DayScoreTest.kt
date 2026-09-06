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
 * 하루 점수를 매기는 규칙입니다.
 *
 * 점수는 사람을 움직이려고 두는 것이라, 규칙이 어긋나면 앱을 믿지 않게 됩니다.
 * 특히 **기분이 점수를 깎지 않는지**를 봅니다. 슬픈 날이 낮은 점수가 되면 힘든 날일수록
 * 앱을 열기 싫어집니다.
 */
class DayScoreTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 9, 5)
    private val threeSteps = listOf(DailyStep.QUOTE, DailyStep.ROUTINE, DailyStep.DIARY)

    @Test
    fun doingNothingScoresZero() {
        val digest = digest(steps = threeSteps)

        assertEquals(0, digest.score.score)
        assertTrue(digest.isEmpty)
    }

    @Test
    fun walkingEveryStepFillsTheBase() {
        val digest = digest(
            steps = threeSteps,
            checks = listOf(check("물 한 컵")),
            diaryList = listOf(diary("오늘")),
            events = listOf(confirmed("글귀"))
        )

        // 고른 걸음을 다 밟으면 뼈대 점수가 찹니다. 덤은 더 한 것이 없어 0입니다.
        assertEquals(DayScore.STEP_POINTS, digest.score.stepScore)
        assertEquals(0, digest.score.bonusScore)
        assertEquals(80, digest.score.score)
    }

    @Test
    fun doingMoreThanTheStepsEarnsTheBonus() {
        // 셋을 밟고 루틴을 둘 더 밟은 날은, 셋만 밟은 날과 같은 점수일 수 없습니다.
        val digest = digest(
            steps = threeSteps,
            checks = listOf(check("물"), check("스트레칭"), check("산책")),
            diaryList = listOf(diary("오늘")),
            events = listOf(confirmed("글귀"))
        )

        assertEquals(5, digest.score.deedCount)
        assertEquals(2 * DayScore.BONUS_PER_DEED, digest.score.bonusScore)
        assertEquals(90, digest.score.score)
    }

    @Test
    fun theBonusStopsSoItDoesNotBecomeNumberChasing() {
        val digest = digest(
            steps = threeSteps,
            checks = (1..20).map { check("루틴 $it") },
            diaryList = listOf(diary("오늘")),
            events = listOf(confirmed("글귀"))
        )

        assertEquals(DayScore.BONUS_CAP, digest.score.bonusScore)
        assertEquals(100, digest.score.score)
    }

    @Test
    fun halfTheStepsIsAboutHalfTheBase() {
        val digest = digest(steps = threeSteps, checks = listOf(check("물")))

        // 셋 중 하나. 80 / 3 = 26.
        assertEquals(26, digest.score.stepScore)
        assertEquals(26, digest.score.score)
    }

    @Test
    fun aSadDayIsNotAWorseDay() {
        // 기분은 점수에 넣지 않습니다. 감정을 잘못한 일로 세면 안 됩니다.
        val happy = digest(
            steps = threeSteps,
            checks = listOf(check("물")),
            moods = listOf(moodLog(DiaryMood.GREAT))
        )
        val sad = digest(
            steps = threeSteps,
            checks = listOf(check("물")),
            moods = listOf(moodLog(DiaryMood.SAD))
        )

        assertEquals(happy.score.score, sad.score.score)
        // 그래도 곁에는 남습니다. 어떤 기분의 날에 무엇을 했는지 보라는 것입니다.
        assertEquals(DiaryMood.SAD, sad.score.mood)
    }

    @Test
    fun aStepYouDidNotChooseDoesNotFillTheBase() {
        // 할 일을 축으로 삼지 않은 사람이 할 일을 끝냈다고 걸음이 채워지면,
        // 자기가 고른 축과 점수가 어긋납니다.
        val digest = digest(
            steps = listOf(DailyStep.QUOTE, DailyStep.ROUTINE),
            todoList = listOf(todoDone("장보기"))
        )

        assertEquals(0, digest.score.stepScore)
        // 다만 한 일로는 셉니다. 걸음 수(2)를 넘지 않아 덤도 0입니다.
        assertEquals(1, digest.score.deedCount)
        assertEquals(0, digest.score.bonusScore)
    }

    @Test
    fun theDigestSaysWhatWasDone() {
        // 점수만 보면 "왜 이 점수지?"를 알 수 없습니다.
        val digest = digest(
            steps = threeSteps,
            checks = listOf(check("물 한 컵"), check("물 한 컵"), check("스트레칭")),
            todoList = listOf(todoDone("장보기")),
            diaryList = listOf(diary("긴 하루")),
            events = listOf(confirmed("오늘의 기준"))
        )

        // 같은 루틴을 두 번 밟았어도 이름은 한 줄입니다.
        assertEquals(listOf("물 한 컵", "스트레칭"), digest.routineTitles)
        assertEquals(listOf("장보기"), digest.todoTitles)
        assertEquals(listOf("긴 하루"), digest.diaryTitles)
        assertEquals(listOf("오늘의 기준"), digest.quoteTitles)
    }

    @Test
    fun yesterdaysRecordsDoNotLandOnToday() {
        val digest = digest(
            steps = threeSteps,
            checks = listOf(check("어제 물", at(today.minusDays(1)))),
            events = listOf(confirmed("어제 글귀", at(today.minusDays(1))))
        )

        assertEquals(0, digest.score.score)
        assertTrue(digest.routineTitles.isEmpty())
    }

    @Test
    fun aTodoCountsOnTheDayItWasFinished() {
        // 지난주 할 일을 오늘 끝냈으면 오늘 한 것입니다.
        val digest = digest(
            steps = listOf(DailyStep.TODO),
            todoList = listOf(
                todoDone("지난주 할 일", dueDate = today.minusDays(7), doneAt = at(today))
            )
        )

        assertEquals(80, digest.score.score)
    }

    // ---- 흐름 ----

    @Test
    fun theTrendHasOnePointPerDay() {
        val trend = DayScoreCalculator.trend(
            today = today,
            events = emptyList(),
            routineChecks = emptyList(),
            todos = emptyList(),
            diaries = emptyList(),
            steps = threeSteps,
            zoneId = zone
        )

        assertEquals(DayScoreCalculator.TREND_DAY_COUNT, trend.points.size)
        assertEquals(today, trend.points.last().date)
        // 빈 날도 담습니다. 빼 버리면 안 한 날이 그래프에서 사라져 흐름이 실제보다 좋아 보입니다.
        assertFalse(trend.hasShape)
    }

    @Test
    fun theTrendComparesThisWeekWithLastWeek() {
        // "어떻게 변해가는지"는 오늘 점수가 아니라 지난주와의 차이가 말해 줍니다.
        val thisWeek = (0..6).map { check("이번 주", at(today.minusDays(it.toLong()))) }
        val lastWeekOnce = listOf(check("지난주", at(today.minusDays(9))))

        val trend = DayScoreCalculator.trend(
            today = today,
            events = emptyList(),
            routineChecks = thisWeek + lastWeekOnce,
            todos = emptyList(),
            diaries = emptyList(),
            steps = listOf(DailyStep.ROUTINE),
            zoneId = zone
        )

        assertEquals(80, trend.thisWeekAverage)
        // 이레 중 하루만 했으니 80 / 7 = 11.
        assertEquals(11, trend.lastWeekAverage)
        assertEquals(69, trend.change)
        assertTrue(trend.hasComparison)
    }

    @Test
    fun withoutALastWeekThereIsNoChangeToSpeakOf() {
        val trend = DayScoreCalculator.trend(
            today = today,
            events = emptyList(),
            routineChecks = listOf(check("오늘", at(today))),
            todos = emptyList(),
            diaries = emptyList(),
            steps = listOf(DailyStep.ROUTINE),
            zoneId = zone
        )

        assertFalse("견줄 지난주가 없는데 변화를 말합니다.", trend.hasComparison)
    }

    @Test
    fun anEmptyStepListFallsBackToTheDefaults() {
        // 걸음이 없으면 0으로 나눕니다. 설정이 비어도 앱이 서면 안 됩니다.
        val digest = digest(steps = emptyList(), checks = listOf(check("물")))

        assertEquals(DailyStep.DEFAULTS, digest.score.steps)
        assertTrue(digest.score.score > 0)
    }

    @Test
    fun aDayWithoutAMoodJustHasNoMood() {
        assertNull(digest(steps = threeSteps, checks = listOf(check("물"))).score.mood)
    }

    // ---- 거들기 ----

    private fun digest(
        steps: List<DailyStep>,
        checks: List<RoutineCheckEntity> = emptyList(),
        todoList: List<TodoEntity> = emptyList(),
        diaryList: List<DiaryEntity> = emptyList(),
        events: List<ExposureEventEntity> = emptyList(),
        moods: List<MoodLogEntity> = emptyList()
    ): DayDigest = DayScoreCalculator.digest(
        date = today,
        events = events,
        routineChecks = checks,
        todos = todoList,
        diaries = diaryList,
        moodLogs = moods,
        steps = steps,
        zoneId = zone
    )

    private fun at(date: LocalDate, time: LocalTime = LocalTime.NOON): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    private fun check(title: String, checkedAt: Long = at(today)) = RoutineCheckEntity(
        syncId = "c-$title-$checkedAt",
        routineId = 1,
        routineSyncId = "r1",
        routineTitle = title,
        checkedAt = checkedAt
    )

    private fun todoDone(
        title: String,
        dueDate: LocalDate = today,
        doneAt: Long = at(today)
    ) = TodoEntity(
        syncId = "t-$title",
        title = title,
        dueDate = dueDate.toString(),
        doneAt = doneAt
    )

    private fun diary(title: String, date: LocalDate = today) = DiaryEntity(
        syncId = "d-$title",
        entryDate = date.toString(),
        title = title,
        body = "본문"
    )

    private fun confirmed(title: String, occurredAt: Long = at(today)) = ExposureEventEntity(
        syncId = "e-$title-$occurredAt",
        contentItemId = 1,
        contentItemSyncId = "i1",
        contentTitle = title,
        contentType = ContentType.QUOTE,
        eventType = ExposureEventType.CONFIRMED,
        trigger = ExposureTrigger.MANUAL_REFRESH,
        occurredAt = occurredAt
    )

    private fun moodLog(mood: DiaryMood, date: LocalDate = today) = MoodLogEntity(
        syncId = "m-$mood",
        entryDate = date.toString(),
        mood = mood.name
    )
}
