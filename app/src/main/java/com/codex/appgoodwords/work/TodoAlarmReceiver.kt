package com.codex.appgoodwords.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.codex.appgoodwords.AppGoodWordsApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 할 일 알람이 울릴 때와, 기기를 껐다 켠 뒤에 불립니다.
 *
 * AlarmManager 예약은 재부팅하면 모두 사라집니다. 다시 걸어 주지 않으면
 * 사용자는 알람을 맞춰 뒀는데 아무 일도 일어나지 않는 경험을 하게 됩니다.
 */
class TodoAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> rescheduleAll(context)
            ACTION_TODO_ALARM -> showAlarm(context, intent)
        }
    }

    private fun showAlarm(context: Context, intent: Intent) {
        val todoId = intent.getLongExtra(EXTRA_TODO_ID, 0L)
        val title = intent.getStringExtra(EXTRA_TODO_TITLE).orEmpty()
        if (title.isBlank()) return

        AppNotifications.showTodoNotification(
            context = context,
            todoId = todoId,
            title = title,
            note = intent.getStringExtra(EXTRA_TODO_NOTE).orEmpty()
        )
    }

    private fun rescheduleAll(context: Context) {
        val application = context.applicationContext as? AppGoodWordsApplication ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = application.container
                container.todoAlarmScheduler.syncAll(container.repository.getPendingTodoReminders())
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TODO_ALARM = "com.codex.appgoodwords.action.TODO_ALARM"
        const val EXTRA_TODO_ID = "todo_id"
        const val EXTRA_TODO_TITLE = "todo_title"
        const val EXTRA_TODO_NOTE = "todo_note"
    }
}
