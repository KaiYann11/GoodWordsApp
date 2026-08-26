package com.codex.appgoodwords.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.codex.appgoodwords.AppGoodWordsApplication
import com.codex.appgoodwords.data.ReportPeriod

/**
 * 주기가 돌아오면 성장 피드백을 만들고 알려 줍니다.
 *
 * 주기가 이레·한 달이어도 **매일 깨어납니다.** 그 사이 폰이 꺼져 있거나 절전에 걸리면
 * 이레짜리 예약은 다음 차례가 통째로 밀리기 때문입니다. 깨어나서는 오늘이 그날인지만 봅니다.
 */
class GrowthFeedbackWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as AppGoodWordsApplication).container
        val settings = container.settingsStore.getAiFeedbackSettings()
        val period = settings.scheduledPeriod ?: return Result.success()

        // 오늘 몫을 이미 만들었으면 다시 부르지 않습니다. 값이 두 번 나갑니다.
        if (!container.growthFeedbackCoordinator.isDue()) return Result.success()

        return try {
            val report = container.growthFeedbackCoordinator.generate(period)
            AppNotifications.showGrowthFeedbackNotification(
                context = applicationContext,
                report = report,
                soundEnabled = container.settingsStore.getSettings().notificationSoundEnabled
            )
            Result.success()
        } catch (failure: Exception) {
            // 사유는 설정 화면에 남습니다(GrowthFeedbackCoordinator가 적어 둡니다).
            // 인터넷이 잠깐 끊겼을 수 있으니 한 번 더 해 봅니다.
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 2
    }
}

/** 손으로 부를 때 쓰는 기간. 설정에서 주기를 끄고도 언제든 부를 수 있어야 합니다. */
internal val manualReportPeriod = ReportPeriod.MANUAL
