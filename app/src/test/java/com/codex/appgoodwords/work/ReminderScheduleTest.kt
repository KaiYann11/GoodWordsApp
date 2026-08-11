package com.codex.appgoodwords.work

import com.codex.appgoodwords.data.ReminderSettings
import java.time.Duration
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 알림이 언제 오는지를 정하는 계산입니다.
 *
 * 여기가 틀리면 새벽에 알림이 오거나 하루 종일 아무것도 오지 않습니다.
 * 둘 다 사용자가 앱을 지우는 이유입니다.
 */
class ReminderScheduleTest {
    private val nineToTen = ReminderSettings(
        intervalMinutes = 60,
        preferredHour = 9,
        preferredMinute = 0,
        repeatEndHour = 22,
        repeatEndMinute = 0
    )

    @Test
    fun beforeTheWindowItWaitsForTheStart() {
        val delay = ReminderSchedule.nextReminderDelay(nineToTen, at("2026-08-10T07:30"))

        assertEquals("09:00까지 1시간 30분", Duration.ofMinutes(90), delay)
    }

    @Test
    fun insideTheWindowItGoesToTheNextStep() {
        // 09:00에서 한 시간 간격이면 다음은 12:00이다.
        val delay = ReminderSchedule.nextReminderDelay(nineToTen, at("2026-08-10T11:20"))

        assertEquals(Duration.ofMinutes(40), delay)
    }

    @Test
    fun theStepFollowsTheStartNotTheCurrentTime() {
        // 시작이 09:10이면 칸은 09:10, 10:10, ... 이어야 한다. 11:00이 아니다.
        val settings = nineToTen.copy(preferredMinute = 10)

        val delay = ReminderSchedule.nextReminderDelay(settings, at("2026-08-10T10:00"))

        assertEquals("10:10까지 10분", Duration.ofMinutes(10), delay)
    }

    @Test
    fun afterTheWindowItWaitsForTomorrow() {
        val delay = ReminderSchedule.nextReminderDelay(nineToTen, at("2026-08-10T23:30"))

        assertEquals("다음 날 09:00까지 9시간 30분", Duration.ofMinutes(9 * 60 + 30), delay)
    }

    @Test
    fun aWindowCrossingMidnightStaysOpen() {
        // 22:00~06:00. 새벽 2시는 어제 시작한 창 안이다.
        val night = ReminderSettings(
            intervalMinutes = 60,
            preferredHour = 22,
            preferredMinute = 0,
            repeatEndHour = 6,
            repeatEndMinute = 0
        )

        val delay = ReminderSchedule.nextReminderDelay(night, at("2026-08-10T02:20"))

        assertEquals("03:00까지 40분", Duration.ofMinutes(40), delay)
    }

    @Test
    fun theLastStepDoesNotSpillPastTheWindowEnd() {
        // 09:00~22:00, 6시간 간격이면 09/15/21시가 마지막이고 다음은 내일 09:00이다.
        val settings = nineToTen.copy(intervalMinutes = 360)

        val delay = ReminderSchedule.nextReminderDelay(settings, at("2026-08-10T21:30"))

        assertEquals("다음 날 09:00까지 11시간 30분", Duration.ofMinutes(11 * 60 + 30), delay)
    }

    @Test
    fun anIntervalBelowTheMinimumIsRaised() {
        // WorkManager가 15분보다 짧은 주기를 받지 않으므로 설정도 그 밑으로 못 내려간다.
        val settings = nineToTen.copy(intervalMinutes = 1)

        val delay = ReminderSchedule.nextReminderDelay(settings, at("2026-08-10T09:05"))

        assertEquals("09:15까지 10분", Duration.ofMinutes(10), delay)
    }

    @Test
    fun theDelayIsNeverNegative() {
        // 음수가 나오면 WorkManager가 즉시 실행해 알림이 쏟아진다.
        val settings = nineToTen.copy(intervalMinutes = 15)
        var now = at("2026-08-10T00:00")

        repeat(24 * 4) {
            val delay = ReminderSchedule.nextReminderDelay(settings, now)
            assertTrue("$now 에서 음수가 나왔습니다: $delay", !delay.isNegative)
            now = now.plusMinutes(15)
        }
    }

    @Test
    fun dailySummaryWaitsForTodayWhenItHasNotPassed() {
        val delay = ReminderSchedule.nextDailyDelay(hour = 21, minute = 0, now = at("2026-08-10T20:15"))

        assertEquals(Duration.ofMinutes(45), delay)
    }

    @Test
    fun dailySummaryMovesToTomorrowOnceItHasPassed() {
        val delay = ReminderSchedule.nextDailyDelay(hour = 21, minute = 0, now = at("2026-08-10T21:00"))

        assertEquals("정각에 다시 예약하면 같은 날 두 번 울린다", Duration.ofHours(24), delay)
    }

    private fun at(text: String): LocalDateTime = LocalDateTime.parse(text)
}
