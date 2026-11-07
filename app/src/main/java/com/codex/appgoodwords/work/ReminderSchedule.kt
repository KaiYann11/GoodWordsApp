package com.codex.appgoodwords.work

import com.codex.appgoodwords.data.ReminderSettings
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
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

    /**
     * 하루 한 번 울리는 알람([DailyAlarm])의 다음 시각(epoch 밀리초). 오늘 그 시각이 지났으면 내일입니다.
     *
     * **언제 물어도 같은 시각이 나와야 합니다.** 앱이 켜질 때마다 다시 거는데, 1.18.0까지의 WorkManager 예약은
     * 켤 때마다 앞당겨졌습니다([DailyAlarm]). 벽시계로 셉니다 — 서머타임이 있는 곳에서 "24시간 뒤"로 세면 한 시간 어긋납니다.
     */
    fun nextDailyAt(
        hour: Int,
        minute: Int,
        nowMillis: Long,
        zone: ZoneId
    ): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val time = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        var next = now.toLocalDate().atTime(time).atZone(zone)

        if (!next.isAfter(now)) {
            next = now.toLocalDate().plusDays(1).atTime(time).atZone(zone)
        }

        return next.toInstant().toEpochMilli()
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
