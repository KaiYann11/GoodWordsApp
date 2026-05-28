package com.codex.appgoodwords.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.codex.appgoodwords.AppGoodWordsApplication
import com.codex.appgoodwords.data.ExposureTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AppNotifications.actionMarkRead &&
            intent.action != AppNotifications.actionCheckRoutine
        ) {
            return
        }

        val contentItemId = intent.getLongExtra(AppNotifications.extraContentId, 0L)
        val routineId = intent.getLongExtra(AppNotifications.extraRoutineId, 0L)
        val shouldRecordView = intent.getBooleanExtra(AppNotifications.extraRecordView, true)
        val notificationId = intent.getIntExtra(AppNotifications.extraNotificationId, 0)
        if (contentItemId <= 0L && routineId <= 0L) return

        val pendingResult = goAsync()
        val application = context.applicationContext as AppGoodWordsApplication

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    AppNotifications.actionMarkRead -> {
                        if (shouldRecordView) {
                            application.container.repository.recordContentViewed(
                                contentItemId = contentItemId,
                                trigger = ExposureTrigger.NOTIFICATION_TAP
                            )
                        }

                        application.container.repository.markContentConfirmed(
                            contentItemId = contentItemId,
                            trigger = ExposureTrigger.NOTIFICATION_TAP
                        )
                    }

                    AppNotifications.actionCheckRoutine -> {
                        application.container.repository.markRoutineDone(routineId)
                    }
                }

                if (notificationId != 0) {
                    NotificationManagerCompat.from(context).cancel(notificationId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
