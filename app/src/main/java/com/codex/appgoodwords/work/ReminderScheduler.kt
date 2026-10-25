package com.codex.appgoodwords.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.codex.appgoodwords.data.AiFeedbackSettings
import com.codex.appgoodwords.data.DailyCoachState
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

    /**
     * 오늘의 한마디를 앱을 닫아 두어도 주기마다 받는 예약([DailyCoachWorker]).
     *
     * 앱을 켤 때와 AI 설정을 바꿀 때 다시 겁니다. 이미 걸려 있던 것은 지우고 새로 겁니다 — 주기를 바꿨는데
     * 옛 주기의 예약이 그대로 남아 있으면 바꾼 값이 다음 한 번을 지나야 먹힙니다.
     */
    fun syncDailyCoach(
        settings: AiFeedbackSettings,
        state: DailyCoachState,
        now: Long = System.currentTimeMillis()
    ) {
        if (!settings.coachRunsInBackground) {
            WorkManager.getInstance(context).cancelUniqueWork(dailyCoachWorkName)
            return
        }
        enqueueDailyCoach(
            delayMillis = DailyCoachSchedule.delayMillis(settings.gapMinutes, state.lastAskedAt, now),
            policy = ExistingWorkPolicy.REPLACE
        )
    }

    /**
     * 일꾼이 한 번 돈 뒤 다음 차례를 겁니다.
     *
     * **뒤에 잇습니다**(`APPEND_OR_REPLACE`). 돌고 있는 일꾼이 같은 이름으로 바꿔 걸면 자기 자신을 지웁니다.
     */
    fun scheduleNextDailyCoach(settings: AiFeedbackSettings) {
        if (!settings.coachRunsInBackground) return
        enqueueDailyCoach(
            delayMillis = settings.gapMinutes * 60_000L,
            policy = ExistingWorkPolicy.APPEND_OR_REPLACE
        )
    }

    /**
     * 홈에서 묻다 끊긴 것을 배경 일꾼이 이어 묻게 합니다([com.codex.appgoodwords.data.DailyCoachCoordinator.refresh]).
     *
     * **주기 예약과 이름을 따로 둡니다.** 같은 이름으로 바꿔 걸면 그때 돌고 있던 일꾼이 지워지고, 뒤에 이으면
     * 주기 예약이 돈 다음에야 묻습니다. 이미 걸어 둔 것이 있으면 그대로 둡니다 — 끊긴 물음은 하나만 남습니다
     * ([DailyCoachState.interruptedAsk]). 이 한 번은 다음 차례를 잇지 않습니다.
     */
    fun handOverDailyCoach() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            dailyCoachHandOverWorkName,
            ExistingWorkPolicy.KEEP,
            dailyCoachRequest(delayMillis = DailyCoachSchedule.MIN_DELAY_MILLIS, handOver = true)
        )
    }

    private fun enqueueDailyCoach(delayMillis: Long, policy: ExistingWorkPolicy) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            dailyCoachWorkName,
            policy,
            dailyCoachRequest(delayMillis = delayMillis, handOver = false)
        )
    }

    private fun dailyCoachRequest(delayMillis: Long, handOver: Boolean) =
        OneTimeWorkRequestBuilder<DailyCoachWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            // 인터넷이 끊긴 채로 깨우면 횟수만 쓰고 실패합니다.
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(workDataOf(DailyCoachWorker.KEY_HAND_OVER to handOver))
            .build()

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
        const val dailyCoachWorkName = "good_words_daily_coach"
        const val dailyCoachHandOverWorkName = "good_words_daily_coach_hand_over"
    }
}
