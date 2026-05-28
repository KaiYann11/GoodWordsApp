package com.codex.appgoodwords.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.codex.appgoodwords.AppGoodWordsApplication

class DailySummaryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val application = applicationContext as AppGoodWordsApplication
        val settings = application.container.settingsStore.getSettings()

        if (!settings.dailySummaryEnabled) {
            return Result.success()
        }

        val summary = application.container.repository.buildTodaySummary()
        AppNotifications.showDailySummaryNotification(
            context = applicationContext,
            summary = summary,
            soundEnabled = settings.notificationSoundEnabled
        )
        return Result.success()
    }
}
