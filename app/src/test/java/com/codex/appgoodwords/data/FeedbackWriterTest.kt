package com.codex.appgoodwords.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 홈에서 짚어 주는 문구입니다.
 *
 * 여기서 지키려는 것은 두 가지입니다. 하나는 사실이 맞아야 한다는 것이고,
 * 다른 하나는 다그치지 않아야 한다는 것입니다. 못 한 것을 세어 보여 주면 할 일 목록이 되고,
 * 하루 빠뜨린 날에는 앱을 열기가 싫어집니다.
 */
class FeedbackWriterTest {
    private val today = LocalDate.of(2026, 8, 23)
    private val zone = ZoneId.of("Asia/Seoul")

    @Test
    fun anEmptyAppGetsOneInvitationInsteadOfNumbers() {
        val notes = write(summary = summary())

        assertEquals(1, notes.size)
        assertEquals(FeedbackKind.INVITE, notes.single().kind)
    }

    @Test
    fun theFirstLineIsHowManyDaysInARow() {
        val notes = write(summary = summary(streak = 5, confirmed = 12))

        assertEquals(FeedbackKind.STREAK, notes.first().kind)
        assertTrue(notes.first().text, notes.first().text.contains("5일째"))
    }

    @Test
    fun aBrokenStreakIsToldWithoutBlame() {
        val notes = write(summary = summary(streak = 0, best = 9, confirmed = 3))
        val streakLine = notes.first { it.kind == FeedbackKind.STREAK }.text

        // "끊겼습니다"라고 말하지 않습니다. 지난 기록을 등에 대 주고 오늘을 권합니다.
        assertTrue(streakLine, streakLine.contains("9일"))
        assertTrue(streakLine, streakLine.contains("다시 시작"))
    }

    @Test
    fun theBusiestDayOfTheWeekIsNamedInKorean() {
        val notes = write(
            summary = summary(
                streak = 2,
                confirmed = 8,
                recentDays = listOf(
                    day(today.minusDays(2), confirmed = 1),
                    // 화요일에 몰아서 했습니다.
                    day(LocalDate.of(2026, 8, 18), confirmed = 6)
                )
            )
        )

        val praise = notes.first { it.kind == FeedbackKind.PRAISE }.text
        assertTrue(praise, praise.contains("화요일"))
        assertTrue(praise, praise.contains("6건"))
    }

    @Test
    fun oneBusyDayAloneIsNotCalledTheBusiestDay() {
        // 기록한 날이 하루뿐이면 "가장 많이 한 날"이라고 부를 것이 못 됩니다.
        val notes = write(
            summary = summary(
                streak = 1,
                confirmed = 3,
                recentDays = listOf(day(today, confirmed = 3))
            )
        )

        assertTrue(notes.none { it.text.contains("가장 많이") })
    }

    @Test
    fun aRoutineKeptEveryDayThisWeekIsPraised() {
        val kept = routine(id = 1, title = "물 한 컵", createdAt = today.minusDays(60))
        val checks = (0 until 7).map { check(routineId = 1, date = today.minusDays(it.toLong())) }

        val notes = write(
            summary = summary(streak = 7, routineChecks = 7),
            routines = listOf(kept),
            checks = checks
        )

        assertTrue(notes.any { it.text.contains("'물 한 컵'은 이레 내내 지켰습니다.") })
    }

    @Test
    fun aQuietRoutineIsNudgedGentlyAndOnlyOnce() {
        val kept = routine(id = 1, title = "물 한 컵", createdAt = today.minusDays(60))
        val quietA = routine(id = 2, title = "스트레칭", createdAt = today.minusDays(60), orderIndex = 1)
        val quietB = routine(id = 3, title = "산책", createdAt = today.minusDays(60), orderIndex = 2)

        val notes = write(
            summary = summary(streak = 3, routineChecks = 5, todoOverdue = 4),
            routines = listOf(kept, quietA, quietB),
            checks = listOf(check(routineId = 1, date = today))
        )

        // 뜸한 것이 둘이고 기한 지난 할 일까지 있어도, 짚는 말은 한 줄까지입니다.
        assertEquals(1, notes.count { it.kind == FeedbackKind.NUDGE })
        val nudge = notes.first { it.kind == FeedbackKind.NUDGE }.text
        // 차례가 앞선 것 하나만 짚습니다. 늘어놓으면 그 자체로 밀린 일감이 됩니다.
        assertTrue(nudge, nudge.contains("스트레칭"))
        assertTrue(nudge, nudge.contains("다시 이어집니다"))
    }

    @Test
    fun aRoutineMadeYesterdayIsNotCalledQuiet() {
        val old = routine(id = 1, title = "물 한 컵", createdAt = today.minusDays(60))
        val fresh = routine(id = 2, title = "새 루틴", createdAt = today.minusDays(1), orderIndex = 1)

        val notes = write(
            summary = summary(streak = 2, routineChecks = 3),
            routines = listOf(old, fresh),
            checks = listOf(check(routineId = 1, date = today))
        )

        assertTrue(notes.none { it.text.contains("새 루틴") })
    }

    @Test
    fun neverMoreThanThreeLines() {
        val routines = (1..4).map {
            routine(id = it.toLong(), title = "루틴 $it", createdAt = today.minusDays(60), orderIndex = it - 1)
        }

        val notes = write(
            summary = summary(
                streak = 12,
                confirmed = 40,
                routineChecks = 30,
                recentDays = listOf(day(today, confirmed = 5), day(today.minusDays(1), confirmed = 2)),
                reading = ReadingSummary(readingCount = 1, finishedCount = 3, finishedThisYear = 3, pagesRead = 900, quotesFromBooks = 12),
                diaryDays = 9,
                todoOverdue = 2
            ),
            routines = routines,
            checks = (0 until 7).map { check(routineId = 1, date = today.minusDays(it.toLong())) }
        )

        assertTrue("문구가 ${notes.size}줄입니다.", notes.size <= FeedbackWriter.MAX_NOTES)
    }

    private fun write(
        summary: StatsSummary,
        progress: DailyProgress = DailyProgress(
            steps = DailyStep.DEFAULTS,
            doneSteps = emptySet(),
            streakDays = summary.currentStreakDays,
            bestStreakDays = summary.bestStreakDays
        ),
        routines: List<RoutineEntity> = emptyList(),
        checks: List<RoutineCheckEntity> = emptyList()
    ) = FeedbackWriter.write(
        summary = summary,
        progress = progress,
        routines = routines,
        routineChecks = checks,
        today = today,
        zoneId = zone
    )

    private fun summary(
        streak: Int = 0,
        best: Int = 0,
        confirmed: Int = 0,
        routineChecks: Int = 0,
        recentDays: List<DailyCount> = emptyList(),
        reading: ReadingSummary = ReadingSummary(0, 0, 0, 0, 0),
        diaryDays: Int = 0,
        todoOverdue: Int = 0
    ) = StatsSummary(
        currentStreakDays = streak,
        bestStreakDays = best,
        activeDays = recentDays.count { it.total > 0 },
        confirmedTotal = confirmed,
        routineCheckTotal = routineChecks,
        recentDays = recentDays,
        topCategories = emptyList(),
        reading = reading,
        diary = DiarySummary(totalCount = diaryDays, daysThisMonth = diaryDays, topMoods = emptyList()),
        todo = TodoSummary(doneCount = 0, openCount = todoOverdue, overdueCount = todoOverdue)
    )

    private fun day(date: LocalDate, confirmed: Int) = DailyCount(date = date, confirmedCount = confirmed, routineCheckCount = 0)

    private fun routine(id: Long, title: String, createdAt: LocalDate, orderIndex: Int = 0) = RoutineEntity(
        id = id,
        syncId = "routine-$id",
        updatedAt = millis(createdAt),
        title = title,
        orderIndex = orderIndex,
        createdAt = millis(createdAt)
    )

    private fun check(routineId: Long, date: LocalDate) = RoutineCheckEntity(
        id = routineId * 100 + date.dayOfYear,
        syncId = "check-$routineId-${date.dayOfYear}",
        routineId = routineId,
        routineSyncId = "routine-$routineId",
        routineTitle = "루틴",
        checkedAt = millis(date)
    )

    /** 그날 정오. 시간대 경계에서 하루가 밀리지 않도록 한낮으로 잡습니다. */
    private fun millis(date: LocalDate): Long =
        date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
}
