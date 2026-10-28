package com.codex.appgoodwords.work

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.codex.appgoodwords.data.AiFeedbackSettings
import com.codex.appgoodwords.data.DailyCoachState
import com.codex.appgoodwords.data.ReminderSettings
import com.codex.appgoodwords.data.ServerSyncSettings
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class ReminderScheduler(
    private val context: Context
) {
    private val dailyCoachAlarm = DailyCoachAlarm(context)

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
     * 오늘의 한마디를 앱을 닫아 두어도 주기마다 받는 알람([DailyCoachAlarm]).
     *
     * 앱을 켤 때·나갈 때, AI 설정을 바꿀 때, 기기를 다시 켰을 때 지금 상태에 맞춰 다시 겁니다. 알람은 하나뿐이라
     * 다시 걸면 앞의 것을 덮어씁니다 — 주기를 바꿨는데 옛 주기의 알람이 남아 있으면 바꾼 값이 다음 한 번을 지나야
     * 먹힙니다. 하루 몫을 다 썼으면 새 하루가 열릴 때까지 깨우지 않습니다.
     */
    fun syncDailyCoach(
        settings: AiFeedbackSettings,
        state: DailyCoachState,
        now: Long = System.currentTimeMillis()
    ) {
        // 1.11.1까지는 WorkManager에 미뤄 걸었습니다. 업데이트한 기기에 남아 있으면 알람과 따로 한 번 더 깨웁니다.
        WorkManager.getInstance(context).cancelUniqueWork(legacyDailyCoachWorkName)
        if (!settings.coachRunsInBackground) {
            dailyCoachAlarm.cancel()
            return
        }
        val delay = if (state.askCount >= settings.askLimit) {
            DailyCoachSchedule.untilTomorrowMillis(now, ZoneId.systemDefault())
        } else {
            DailyCoachSchedule.delayMillis(settings.gapMinutes, state.lastAskedAt, now)
        }
        dailyCoachAlarm.set(now + delay)
    }

    /**
     * 일꾼이 한 번 돈 뒤 다음 차례를 겁니다 — 마지막으로 물은 때부터 한 주기 뒤.
     *
     * 물을 때가 지났는데 묻지 않았으면(오늘 한 것이 아직 없으면) 지금부터 한 주기 뒤입니다
     * ([DailyCoachSchedule.nextDelayMillis]).
     */
    fun scheduleNextDailyCoach(
        settings: AiFeedbackSettings,
        state: DailyCoachState,
        now: Long = System.currentTimeMillis()
    ) {
        if (!settings.coachRunsInBackground) return
        val delay = if (state.askCount >= settings.askLimit) {
            DailyCoachSchedule.untilTomorrowMillis(now, ZoneId.systemDefault())
        } else {
            DailyCoachSchedule.nextDelayMillis(settings.gapMinutes, state.lastAskedAt, now)
        }
        dailyCoachAlarm.set(now + delay)
    }

    /**
     * 알람이 울린 참에 다음 차례를 한 주기 뒤로 먼저 걸어 둡니다([DailyCoachAlarmReceiver]).
     *
     * 일꾼이 인터넷을 기다리거나 도중에 멈춰도 알람이 끊기지 않게 하려는 것입니다. 끊기면 앱을 열 때까지 한마디가
     * 오지 않습니다. 일꾼이 다 물은 뒤 [scheduleNextDailyCoach]로 다시 맞춥니다.
     */
    fun armDailyCoachFallback(settings: AiFeedbackSettings, now: Long = System.currentTimeMillis()) {
        if (!settings.coachRunsInBackground) return
        dailyCoachAlarm.set(now + settings.gapMinutes * 60_000L)
    }

    /**
     * 지금 곧바로 묻는 일꾼을 겁니다. 알람이 울렸을 때 부릅니다.
     *
     * **미뤄 두지 않고 곧바로 돌게 합니다**(expedited). 보통 일꾼은 잠자기 중이면 다음 정비 창까지 기다리는데,
     * 곧바로 도는 일꾼은 잠자기 중에도 돌고 인터넷도 열립니다. 하루에 쓸 수 있는 몫을 넘기면 보통 일꾼으로 돕니다.
     * Android 12 아래에서는 곧바로 도는 일꾼이 알림을 띄우는 포그라운드 서비스가 되어야 해서 보통 일꾼으로 겁니다.
     *
     * 앞의 것이 아직 돌거나 인터넷을 기다리고 있으면 그대로 둡니다 — 한 번에 하나만 묻습니다.
     */
    fun runDailyCoachNow() {
        val request = OneTimeWorkRequestBuilder<DailyCoachWorker>()
            .setConstraints(connectedConstraints())
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                }
            }
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(dailyCoachRunWorkName, ExistingWorkPolicy.KEEP, request)
    }

    /**
     * 홈에서 묻다 끊긴 것을 배경 일꾼이 이어 묻게 합니다([com.codex.appgoodwords.data.DailyCoachCoordinator.refresh]).
     *
     * **알람과 이름을 따로 둡니다.** 같은 이름이면 알람이 건 일꾼이 기다리는 동안 이것이 묻히거나 그 반대가 됩니다.
     * 이미 걸어 둔 것이 있으면 그대로 둡니다 — 끊긴 물음은 하나만 남습니다([DailyCoachState.interruptedAsk]).
     */
    fun handOverDailyCoach() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            dailyCoachHandOverWorkName,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<DailyCoachWorker>()
                .setInitialDelay(DailyCoachSchedule.MIN_DELAY_MILLIS, TimeUnit.MILLISECONDS)
                .setConstraints(connectedConstraints())
                .build()
        )
    }

    /** 인터넷이 끊긴 채로 깨우면 횟수만 쓰고 실패합니다. */
    private fun connectedConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
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
        /** 1.11.1까지 쓰던 미뤄 둔 예약. 지금은 지우기만 합니다([syncDailyCoach]). */
        const val legacyDailyCoachWorkName = "good_words_daily_coach"
        const val dailyCoachRunWorkName = "good_words_daily_coach_run"
        const val dailyCoachHandOverWorkName = "good_words_daily_coach_hand_over"
    }
}
