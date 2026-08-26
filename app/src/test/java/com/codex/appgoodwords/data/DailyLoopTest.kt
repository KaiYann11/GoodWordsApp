package com.codex.appgoodwords.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 오늘의 걸음이 제대로 세어지는지 봅니다.
 *
 * 이 카드의 목적은 다그치는 것이 아니라 다음 한 걸음을 알려 주는 것입니다. 그래서
 * "무엇이 남았는지"보다 **이어 온 날이 끊기지 않는지**를 특히 봅니다. 하루를 잘못 세면
 * 사용자는 지키고 있던 것이 사라졌다고 느낍니다.
 */
class DailyLoopTest {
    private val zone: ZoneId = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 8, 18)

    @Test
    fun nothingDoneYetPointsAtTheFirstStep() {
        val progress = build()

        assertEquals(0, progress.doneCount)
        assertEquals(DailyStep.QUOTE, progress.nextStep)
        assertFalse(progress.isComplete)
        assertEquals(0f, progress.ratio, 0.001f)
    }

    @Test
    fun eachStepIsCountedFromItsOwnRecord() {
        val progress = build(
            confirmedOn = listOf(today),
            checkedOn = listOf(today),
            diaryOn = listOf(today)
        )

        assertEquals(setOf(DailyStep.QUOTE, DailyStep.ROUTINE, DailyStep.DIARY), progress.doneSteps)
        assertTrue(progress.isComplete)
        assertEquals(null, progress.nextStep)
        assertEquals(1f, progress.ratio, 0.001f)
    }

    @Test
    fun yesterdaysRecordsDoNotCountForToday() {
        val progress = build(
            confirmedOn = listOf(today.minusDays(1)),
            checkedOn = listOf(today.minusDays(1)),
            diaryOn = listOf(today.minusDays(1))
        )

        assertEquals(0, progress.doneCount)
    }

    @Test
    fun theNextStepFollowsTheOrder() {
        // 글귀를 건너뛰고 일기부터 썼어도, 다음으로 안내하는 것은 아직 안 한 첫 걸음입니다.
        val progress = build(diaryOn = listOf(today))

        assertEquals(DailyStep.QUOTE, progress.nextStep)
        assertEquals(1, progress.doneCount)
    }

    @Test
    fun onlyDaysWithAllThreeCountTowardTheStreak() {
        val progress = build(
            confirmedOn = listOf(today, today.minusDays(1), today.minusDays(2)),
            checkedOn = listOf(today, today.minusDays(1), today.minusDays(2)),
            // 어제는 일기를 안 썼습니다. 고른 걸음을 다 밟은 날이 아니라 이어짐이 거기서 끊깁니다.
            diaryOn = listOf(today, today.minusDays(2))
        )

        assertEquals(1, progress.streakDays)
    }

    @Test
    fun theStreakSurvivesAMorningWithNothingDoneYet() {
        // 아침에 열었을 때 0일로 보이면 어제까지 쌓은 것이 사라진 것처럼 느껴집니다.
        val yesterday = today.minusDays(1)
        val progress = build(
            confirmedOn = listOf(yesterday, today.minusDays(2)),
            checkedOn = listOf(yesterday, today.minusDays(2)),
            diaryOn = listOf(yesterday, today.minusDays(2))
        )

        assertEquals(2, progress.streakDays)
        assertEquals(0, progress.doneCount)
    }

    @Test
    fun aGapOfTwoDaysEndsTheStreak() {
        val progress = build(
            confirmedOn = listOf(today.minusDays(2)),
            checkedOn = listOf(today.minusDays(2)),
            diaryOn = listOf(today.minusDays(2))
        )

        assertEquals(0, progress.streakDays)
        // 그래도 지난 기록은 최고 기록으로 남습니다.
        assertEquals(1, progress.bestStreakDays)
    }

    @Test
    fun theBestStreakRemembersTheLongestRun() {
        val days = (3..7).map { today.minusDays(it.toLong()) }
        val progress = build(confirmedOn = days, checkedOn = days, diaryOn = days)

        assertEquals(5, progress.bestStreakDays)
        // 사흘 전에 끝났으므로 지금 이어 오는 중은 아닙니다.
        assertEquals(0, progress.streakDays)
    }

    @Test
    fun onlyConfirmedQuotesCount() {
        // 알림으로 보여 준 것만으로는 읽었다고 볼 수 없습니다. 확인을 눌러야 한 걸음입니다.
        val progress = build(shownOn = listOf(today))

        assertFalse(DailyStep.QUOTE in progress.doneSteps)
    }

    @Test
    fun aBrokenDiaryDateDoesNotBreakTheCard() {
        val progress = build(confirmedOn = listOf(today), brokenDiaryDates = listOf("언젠가"))

        assertEquals(1, progress.doneCount)
    }

    @Test
    fun theMessageNamesTheNextStepWhileAStreakIsRunning() {
        val yesterday = today.minusDays(1)
        val progress = build(
            confirmedOn = listOf(today, yesterday),
            checkedOn = listOf(yesterday),
            diaryOn = listOf(yesterday)
        )

        // 이어 온 날이 있으면 그것을 지키는 쪽이 새로 시작하는 것보다 큰 힘이 됩니다.
        assertTrue(progress.message, progress.message.contains("1일째"))
        assertTrue(progress.message, progress.message.contains(DailyStep.ROUTINE.label))
    }

    @Test
    fun theMessageNeverCountsWhatIsMissing() {
        // "2개 남았습니다"는 할 일 목록처럼 들려서, 빠뜨린 날에는 앱을 열기 싫어집니다.
        val progress = build(confirmedOn = listOf(today))

        assertFalse(progress.message, progress.message.contains("남았"))
        assertFalse(progress.message, progress.message.contains("2개"))
    }

    @Test
    fun theAxisOfTheDayIsChosen() {
        // 글귀로 하루를 여는 사람도 있고, 할 일을 끝내는 것이 하루인 사람도 있습니다.
        val progress = build(
            checkedOn = listOf(today),
            steps = listOf(DailyStep.TODO, DailyStep.ROUTINE)
        )

        assertEquals(listOf(DailyStep.TODO, DailyStep.ROUTINE), progress.steps)
        assertEquals(2, progress.stepCount)
        // 고르지 않은 걸음은 세지 않습니다. 글귀를 읽었어도 오늘 밟은 걸음이 아닙니다.
        assertEquals(DailyStep.TODO, progress.nextStep)
        assertEquals(0.5f, progress.ratio, 0.001f)
    }

    @Test
    fun aTodoCountsOnTheDayItWasFinished() {
        // 지난주 할 일을 오늘 끝냈으면 오늘 한 것입니다. 마감일로 세면 오늘이 빈 채로 남습니다.
        val progress = build(todoDoneOn = listOf(today), steps = listOf(DailyStep.TODO))

        assertTrue(progress.isComplete)
    }

    @Test
    fun anUnfinishedTodoIsNotAStep() {
        val progress = DailyLoopCalculator.build(
            events = emptyList(),
            routineChecks = emptyList(),
            todos = listOf(TodoEntity(syncId = "todo-1", title = "할 일", dueDate = today.toString())),
            diaries = emptyList(),
            today = today,
            steps = listOf(DailyStep.TODO),
            zoneId = zone
        )

        assertEquals(0, progress.doneCount)
    }

    @Test
    fun changingTheAxisRecountsThePastRightAway() {
        // 연속 날수는 어디에도 적혀 있지 않고 기록에서 다시 셉니다. 저장해 두면 옛 기준으로
        // 쌓인 숫자와 새 기준이 섞여, 어느 기준의 며칠인지 아무도 모르게 됩니다.
        val days = (0..2).map { today.minusDays(it.toLong()) }

        val withDiary = build(confirmedOn = days, checkedOn = days, diaryOn = listOf(today))
        val withoutDiary = build(
            confirmedOn = days,
            checkedOn = days,
            diaryOn = listOf(today),
            steps = listOf(DailyStep.QUOTE, DailyStep.ROUTINE)
        )

        assertEquals(1, withDiary.streakDays)
        assertEquals(3, withoutDiary.streakDays)
    }

    @Test
    fun anAxisWithNothingOnItFallsBackToTheDefaults() {
        // 걸음이 하나도 없으면 카드가 뜻을 잃습니다. 0으로 나누는 일도 없어야 합니다.
        val progress = build(steps = emptyList())

        assertEquals(DailyStep.DEFAULTS, progress.steps)
        assertEquals(0f, progress.ratio, 0.001f)
        assertFalse(progress.isComplete)
    }

    @Test
    fun theSameStepTwiceIsStillOneStep() {
        val progress = build(
            checkedOn = listOf(today),
            steps = listOf(DailyStep.ROUTINE, DailyStep.ROUTINE)
        )

        assertEquals(1, progress.stepCount)
        assertTrue(progress.isComplete)
    }

    @Test
    fun whatIsStoredComesBackTheSame() {
        val steps = listOf(DailyStep.TODO, DailyStep.DIARY)

        assertEquals(steps, DailyStep.parse(DailyStep.store(steps)))
        // 모르는 이름이 남아 있어도 앱이 서지 않아야 합니다.
        assertEquals(listOf(DailyStep.DIARY), DailyStep.parse("SLEEP,DIARY"))
        assertEquals(DailyStep.DEFAULTS, DailyStep.parse(""))
        assertEquals(DailyStep.DEFAULTS, DailyStep.parse("SLEEP"))
    }

    private fun build(
        confirmedOn: List<LocalDate> = emptyList(),
        shownOn: List<LocalDate> = emptyList(),
        checkedOn: List<LocalDate> = emptyList(),
        diaryOn: List<LocalDate> = emptyList(),
        todoDoneOn: List<LocalDate> = emptyList(),
        steps: List<DailyStep> = DailyStep.DEFAULTS,
        brokenDiaryDates: List<String> = emptyList()
    ): DailyProgress = DailyLoopCalculator.build(
        events = confirmedOn.map { event(it, ExposureEventType.CONFIRMED) } +
            shownOn.map { event(it, ExposureEventType.SHOWN) },
        routineChecks = checkedOn.mapIndexed { index, date ->
            RoutineCheckEntity(
                syncId = "check-$index",
                routineId = 1,
                routineSyncId = "routine-1",
                routineTitle = "산책",
                checkedAt = millisAt(date)
            )
        },
        todos = todoDoneOn.mapIndexed { index, date ->
            TodoEntity(
                syncId = "todo-$index",
                title = "할 일 $index",
                // 마감일은 일부러 다른 날로 둡니다. 끝낸 날로 세는지 보려는 것입니다.
                dueDate = date.minusDays(3).toString(),
                doneAt = millisAt(date)
            )
        },
        diaries = diaryOn.map { DiaryEntity(syncId = "diary-$it", entryDate = it.toString()) } +
            brokenDiaryDates.mapIndexed { index, raw ->
                DiaryEntity(syncId = "broken-$index", entryDate = raw)
            },
        today = today,
        steps = steps,
        zoneId = zone
    )

    private fun event(date: LocalDate, type: ExposureEventType) = ExposureEventEntity(
        syncId = "event-$date-$type",
        contentItemId = 1,
        contentItemSyncId = "item-1",
        contentTitle = "글귀",
        contentType = ContentType.QUOTE,
        eventType = type,
        trigger = ExposureTrigger.APP_LAUNCH,
        occurredAt = millisAt(date)
    )

    private fun millisAt(date: LocalDate): Long = date
        .atTime(LocalTime.NOON)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
}
