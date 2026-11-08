package com.codex.appgoodwords.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoalTimeTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 11, 8)

    @Test fun minutesReadAsHoursAndMinutes() {
        assertEquals("45분", GoalTime.format(45))
        assertEquals("1시간", GoalTime.format(60))
        assertEquals("1시간 30분", GoalTime.format(90))
        assertEquals("120시간 30분", GoalTime.format(7_230))
    }

    @Test fun typedTimeIsBetweenAMinuteAndADay() {
        assertEquals(90, GoalTime.parse("1", "30"))
        assertEquals(45, GoalTime.parse("", "45"))
        assertEquals(120, GoalTime.parse("2", " "))
        assertEquals(24 * 60, GoalTime.parse("24", "0"))
        assertNull(GoalTime.parse("", ""))
        assertNull(GoalTime.parse("0", "0"))
        assertNull("하루를 넘으면 받지 않습니다", GoalTime.parse("24", "1"))
        assertNull(GoalTime.parse("한", "30"))
    }

    @Test fun theTimerRoundsUpAndStaysWithinADay() {
        // 42분 10초는 43분입니다. 잰 것보다 적게 적히면 들인 시간이 줄어 보입니다.
        assertEquals(43, GoalTime.elapsedMinutes(0L, 42 * 60_000L + 10_000L))
        assertEquals(1, GoalTime.elapsedMinutes(0L, 10_000L))
        assertEquals(1, GoalTime.elapsedMinutes(5_000L, 1_000L))
        // 끄는 것을 잊고 하루를 넘겨도 하루까지만 — 남기기 전에 고칠 수 있습니다.
        assertEquals(GoalTime.MAX_MINUTES, GoalTime.elapsedMinutes(0L, 30 * 3_600_000L))
    }

    @Test fun theRunningClockShowsHoursMinutesAndSeconds() {
        assertEquals("0:42:10", GoalTime.clock(42 * 60_000L + 10_000L))
        assertEquals("3:05:09", GoalTime.clock(3 * 3_600_000L + 5 * 60_000L + 9_000L))
        assertEquals("0:00:00", GoalTime.clock(-5L))
    }

    @Test fun recentTimeIsTheLastFourWeeksIncludingToday() {
        val logs = listOf(
            log(minutes = 30, day = today),
            log(minutes = 60, day = today.minusDays(27)),
            log(minutes = 500, day = today.minusDays(28))
        )

        assertEquals(90, GoalTime.recent(logs, today, zone))
        assertEquals(590, GoalTime.total(logs))
    }

    @Test fun aTimerHasMeasuredTimeOnlyOnceStopped() {
        val running = GoalTimer(goalSyncId = "goal-1", startedAt = 1_000L)
        assertNull(running.measuredMinutes)
        assertEquals(43, running.copy(stoppedAt = 1_000L + 42 * 60_000L + 10_000L).measuredMinutes)
    }

    private fun log(minutes: Int, day: LocalDate) = GoalTimeLogEntity(
        goalSyncId = "goal-1",
        minutes = minutes,
        loggedAt = day.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
    )
}
