package com.codex.appgoodwords.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.codex.appgoodwords.data.ReminderSettings
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.math.max

class ReminderScheduler(
    private val context: Context
) {
    suspend fun sync(settings: ReminderSettings) {
        val workManager = WorkManager.getInstance(context)

        if (settings.remindersEnabled) {
            val intervalMinutes = settings.effectiveIntervalMinutes
            val reminderRequest = PeriodicWorkRequestBuilder<QuoteReminderWorker>(
                intervalMinutes.toLong(),
                TimeUnit.MINUTES
            )
                .setInitialDelay(
                    calculateRepeatingDelay(settings).toMillis(),
                    TimeUnit.MILLISECONDS
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                reminderWorkName,
                ExistingPeriodicWorkPolicy.UPDATE,
                reminderRequest
            )
        } else {
            workManager.cancelUniqueWork(reminderWorkName)
        }

        if (settings.dailySummaryEnabled) {
            val summaryRequest = PeriodicWorkRequestBuilder<DailySummaryWorker>(
                24,
                TimeUnit.HOURS
            )
                .setInitialDelay(
                    calculateDailyDelay(
                        hour = settings.summaryHour,
                        minute = settings.summaryMinute
                    ).toMillis(),
                    TimeUnit.MILLISECONDS
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                summaryWorkName,
                ExistingPeriodicWorkPolicy.UPDATE,
                summaryRequest
            )
        } else {
            workManager.cancelUniqueWork(summaryWorkName)
        }
    }

    private fun calculateRepeatingDelay(settings: ReminderSettings): Duration {
        val now = LocalDateTime.now()
        val intervalMinutes = settings.effectiveIntervalMinutes.toLong()

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

    private fun buildWindow(
        settings: ReminderSettings,
        date: LocalDate
    ): ReminderWindow {
        val start = date.atTime(settings.preferredHour, settings.preferredMinute)
        var end = date.atTime(settings.repeatEndHour, settings.repeatEndMinute)

        if (!end.isAfter(start)) {
            end = end.plusDays(1)
        }

        return ReminderWindow(start = start, end = end)
    }

    private fun calculateDailyDelay(
        hour: Int,
        minute: Int
    ): Duration {
        val now = LocalDateTime.now()
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

    private data class ReminderWindow(
        val start: LocalDateTime,
        val end: LocalDateTime
    )

    private companion object {
        const val reminderWorkName = "good_words_reminder"
        const val summaryWorkName = "good_words_daily_summary"
    }
}
