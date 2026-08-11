package com.codex.appgoodwords.work

import com.codex.appgoodwords.data.ReminderSettings
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.max

/**
 * 다음 알림까지 얼마나 남았는지 계산합니다.
 *
 * 이 앱이 하는 일이 "정한 시간에 알려 주기"뿐이라 여기가 틀리면 앱이 하는 일이 없어집니다.
 * 시각을 인자로 받는 이유는 그것뿐입니다. 실제로는 [ReminderScheduler]가 지금 시각을 넣습니다.
 */
object ReminderSchedule {
    /**
     * 반복 알림의 첫 실행까지 남은 시간.
     *
     * 반복 창(시작~종료) 안에서 간격에 맞춰 떨어지는 다음 시각을 찾습니다.
     * 오늘 창이 이미 끝났으면 다음 날 창의 시작으로 넘어갑니다.
     */
    fun nextReminderDelay(
        settings: ReminderSettings,
        now: LocalDateTime
    ): Duration {
        val intervalMinutes = settings.effectiveIntervalMinutes.toLong()

        // 창이 자정을 넘으면 어제 시작한 창이 아직 열려 있을 수 있어 하루 전부터 본다.
        for (dayOffset in -1L..2L) {
            val window = buildWindow(settings, now.toLocalDate().plusDays(dayOffset))
            var next = window.start

            if (!next.isAfter(now)) {
                val elapsedMinutes = Duration.between(window.start, now).toMinutes()
                val steps = max(0, elapsedMinutes / intervalMinutes + 1)
                next = window.start.plusMinutes(steps * intervalMinutes)
            }

            if (!next.isAfter(window.end)) {
                return Duration.between(now, next)
            }
        }

        return Duration.ofMinutes(intervalMinutes)
    }

    /** 하루 정리 알림의 다음 실행까지 남은 시간. 오늘 시각이 지났으면 내일입니다. */
    fun nextDailyDelay(
        hour: Int,
        minute: Int,
        now: LocalDateTime
    ): Duration {
        var next = now
            .withHour(hour)
            .withMinute(minute)
            .withSecond(0)
            .withNano(0)

        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }

        return Duration.between(now, next)
    }

    private fun buildWindow(
        settings: ReminderSettings,
        date: LocalDate
    ): ReminderWindow {
        val start = date.atTime(settings.preferredHour, settings.preferredMinute)
        var end = date.atTime(settings.repeatEndHour, settings.repeatEndMinute)

        // 종료가 시작보다 앞이면 자정을 넘는 창이다. 예: 22:00~06:00
        if (!end.isAfter(start)) {
            end = end.plusDays(1)
        }

        return ReminderWindow(start = start, end = end)
    }

    private data class ReminderWindow(
        val start: LocalDateTime,
        val end: LocalDateTime
    )
}
