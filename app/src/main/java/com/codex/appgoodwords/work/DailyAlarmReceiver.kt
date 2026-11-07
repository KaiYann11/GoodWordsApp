package com.codex.appgoodwords.work

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.codex.appgoodwords.AppGoodWordsApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 하루 한 번 울리는 알람([DailyAlarm])이 울릴 때와, 알람을 다시 걸어야 할 때 불립니다.
 *
 * - **일일 집계가 울리면**: 오늘 집계를 알리고 내일 그 시각으로 다시 겁니다. 집계는 DB만 읽어 금방 끝납니다.
 * - **돌아보기가 울리면**: 만들기와 뜸할 때 알리기를 곧바로 도는 일꾼에게 넘기고([ReminderScheduler.runGrowthNow])
 *   내일로 다시 겁니다. 만드는 데 1~2분이 걸리고 인터넷도 기다려야 하는데, 알람을 받는 자리는 몇 초 만에 닫힙니다.
 * - **기기를 다시 켜거나 앱이 새로 깔리면**: AlarmManager 예약은 그때 사라지므로 다시 겁니다.
 * - **정확한 알람 권한을 켜면**: 그때부터 정확한 시각으로 다시 겁니다.
 */
class DailyAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val application = context.applicationContext as? AppGoodWordsApplication ?: return
        val action = intent.action
        if (action != ACTION_DAILY_SUMMARY && action != ACTION_GROWTH && action !in RESYNC_ACTIONS) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = application.container
                val scheduler = container.reminderScheduler
                when (action) {
                    ACTION_DAILY_SUMMARY -> {
                        val settings = container.settingsStore.getSettings()
                        if (settings.dailySummaryEnabled) {
                            AppNotifications.showDailySummaryNotification(
                                context = application,
                                summary = container.repository.buildTodaySummary(),
                                soundEnabled = settings.notificationSoundEnabled
                            )
                        }
                        scheduler.syncDailySummary(settings, now = System.currentTimeMillis() + RE_ARM_MARGIN_MILLIS)
                    }

                    ACTION_GROWTH -> {
                        val settings = container.settingsStore.getAiFeedbackSettings()
                        scheduler.runGrowthNow(settings)
                        scheduler.syncGrowthFeedback(settings, now = System.currentTimeMillis() + RE_ARM_MARGIN_MILLIS)
                    }

                    else -> {
                        scheduler.syncDailySummary(container.settingsStore.getSettings())
                        scheduler.syncGrowthFeedback(container.settingsStore.getAiFeedbackSettings())
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DAILY_SUMMARY = "com.codex.appgoodwords.action.DAILY_SUMMARY"
        const val ACTION_GROWTH = "com.codex.appgoodwords.action.GROWTH"

        /**
         * 울린 참에 다음 차례는 1분 뒤부터 셉니다. 알람이 닿은 때 시계가 그 시각보다 조금이라도 앞서 있으면
         * (시계를 맞추는 중이면) 오늘 그 시각으로 다시 걸려 같은 날 두 번 알립니다.
         */
        private const val RE_ARM_MARGIN_MILLIS = 60_000L

        private val RESYNC_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )
    }
}
