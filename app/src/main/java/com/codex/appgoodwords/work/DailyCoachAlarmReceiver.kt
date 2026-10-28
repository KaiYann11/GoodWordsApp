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
 * 오늘의 한마디 알람이 울릴 때와, 알람을 다시 걸어야 할 때 불립니다.
 *
 * - **울리면**: 다음 차례를 한 주기 뒤로 먼저 걸어 두고, 곧바로 도는 일꾼에게 묻기를 넘깁니다
 *   ([ReminderScheduler.runDailyCoachNow]). 인터넷이 없어 일꾼이 기다리는 동안에도 다음 알람은 남습니다.
 *   일꾼이 다 물은 뒤 마지막으로 물은 때에 맞춰 다시 겁니다.
 * - **기기를 다시 켜거나 앱이 새로 깔리면**: AlarmManager 예약은 그때 사라지므로 다시 겁니다.
 * - **정확한 알람 권한을 켜면**: 그때부터 정확한 시각으로 다시 겁니다.
 */
class DailyCoachAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val application = context.applicationContext as? AppGoodWordsApplication ?: return
        val action = intent.action
        if (action != ACTION_DAILY_COACH && action !in RESYNC_ACTIONS) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = application.container
                val settings = container.settingsStore.getAiFeedbackSettings()
                if (action == ACTION_DAILY_COACH) {
                    if (!settings.coachRunsInBackground) {
                        container.reminderScheduler.syncDailyCoach(settings, container.dailyCoachCoordinator.state())
                        return@launch
                    }
                    container.reminderScheduler.armDailyCoachFallback(settings)
                    container.reminderScheduler.runDailyCoachNow()
                } else {
                    container.reminderScheduler.syncDailyCoach(settings, container.dailyCoachCoordinator.state())
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DAILY_COACH = "com.codex.appgoodwords.action.DAILY_COACH"

        private val RESYNC_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )
    }
}
