package com.codex.appgoodwords.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.codex.appgoodwords.AppGoodWordsApplication
import com.codex.appgoodwords.data.GrowthCadence

/**
 * 오래 돌아보지 않았으면 알려 줍니다.
 *
 * [GrowthFeedbackWorker]와 나눠 둔 이유가 있습니다. 그쪽은 주기가 돌아오면 **피드백을 만듭니다** —
 * 값이 드는 바깥 요청입니다. 이쪽은 아무것도 만들지 않고 알리기만 합니다. 그래서 자동 실행을
 * 꺼 둔 사람에게도 돌 수 있고, 오히려 그런 사람에게 더 필요합니다.
 *
 * 매일 깨어나 오늘이 알릴 날인지만 봅니다. 이레짜리 예약으로 두면 그사이 폰이 꺼져 있거나
 * 절전에 걸렸을 때 다음 차례가 통째로 밀립니다.
 */
class GrowthNudgeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as AppGoodWordsApplication).container
        val aiSettings = container.settingsStore.getAiFeedbackSettings()
        if (!aiSettings.nudgeEnabled) return Result.success()

        val now = System.currentTimeMillis()
        val reports = container.database.growthReportDao().getAll()

        // 뜸하지 않거나, 한 편도 없어 아직 시작하지 않았으면 조용히 둡니다.
        if (!GrowthCadence.isOverdue(reports, now)) return Result.success()
        // 뜸해진 뒤로 매일 알리면 잔소리가 됩니다.
        if (!GrowthCadence.shouldNudgeAgain(aiSettings.lastNudgedAt, now)) return Result.success()

        val days = GrowthCadence.daysSince(reports, now) ?: return Result.success()
        AppNotifications.showGrowthNudgeNotification(
            context = applicationContext,
            daysSince = days,
            soundEnabled = container.settingsStore.getSettings().notificationSoundEnabled
        )
        container.settingsStore.recordGrowthNudge(now)
        return Result.success()
    }
}
