package com.codex.appgoodwords.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.codex.appgoodwords.data.AiFeedbackSettings
import com.codex.appgoodwords.data.ReminderSettings
import com.codex.appgoodwords.data.ServerSyncSettings
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

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

    /** 자동 동기화 예약. 주소가 없거나 꺼져 있으면 예약을 지운다. */
    fun syncAutoSync(settings: ServerSyncSettings) {
        val workManager = WorkManager.getInstance(context)

        if (!settings.canAutoSync) {
            workManager.cancelUniqueWork(autoSyncWorkName)
            return
        }

        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            settings.effectiveIntervalHours.toLong(),
            TimeUnit.HOURS
        )
            // 서버가 같은 LAN에 있어도 네트워크가 끊긴 상태로 깨우면 그냥 실패한다.
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            autoSyncWorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * AI 성장 피드백 예약.
     *
     * 주기가 하루보다 길어도 **하루에 한 번 깨웁니다.** 주 단위 예약을 이레짜리 주기로 걸면,
     * 그 사이 폰이 꺼져 있거나 절전에 걸렸을 때 다음 차례가 이레 뒤로 밀립니다.
     * 매일 깨어나 "오늘이 그날인지"만 보고([AiFeedbackSettings.isDue]) 아니면 그냥 끝냅니다.
     */
    fun syncGrowthFeedback(settings: AiFeedbackSettings) {
        val workManager = WorkManager.getInstance(context)

        if (!settings.isScheduled) {
            workManager.cancelUniqueWork(growthFeedbackWorkName)
            return
        }

        val request = PeriodicWorkRequestBuilder<GrowthFeedbackWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(
                calculateDailyDelay(hour = settings.hour, minute = settings.minute).toMillis(),
                TimeUnit.MILLISECONDS
            )
            // 인터넷이 끊긴 채로 깨우면 값만 쓰고 실패합니다.
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            growthFeedbackWorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * 오래 돌아보지 않았을 때 알리는 예약.
     *
     * [syncGrowthFeedback]과 따로 겁니다. 그쪽은 주기를 켜 둔 사람에게만 돌지만, 이쪽은
     * **자동 실행을 꺼 둔 사람에게 더 필요합니다.** 손으로만 돌리는 사람이 잊는 것이니까요.
     * 인터넷도 필요 없습니다. 아무것도 만들지 않고 알리기만 하기 때문입니다.
     */
    fun syncGrowthNudge(settings: AiFeedbackSettings) {
        val workManager = WorkManager.getInstance(context)

        if (!settings.nudgeEnabled) {
            workManager.cancelUniqueWork(growthNudgeWorkName)
            return
        }

        val request = PeriodicWorkRequestBuilder<GrowthNudgeWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(
                calculateDailyDelay(hour = settings.hour, minute = settings.minute).toMillis(),
                TimeUnit.MILLISECONDS
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            growthNudgeWorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    // 계산은 ReminderSchedule에 있습니다. 시각을 인자로 받아야 테스트할 수 있어서입니다.
    private fun calculateRepeatingDelay(settings: ReminderSettings): Duration =
        ReminderSchedule.nextReminderDelay(settings, LocalDateTime.now())

    private fun calculateDailyDelay(
        hour: Int,
        minute: Int
    ): Duration = ReminderSchedule.nextDailyDelay(hour, minute, LocalDateTime.now())

    private companion object {
        const val reminderWorkName = "good_words_reminder"
        const val summaryWorkName = "good_words_daily_summary"
        const val autoSyncWorkName = "good_words_auto_sync"
        const val growthFeedbackWorkName = "good_words_growth_feedback"
        const val growthNudgeWorkName = "good_words_growth_nudge"
    }
}
