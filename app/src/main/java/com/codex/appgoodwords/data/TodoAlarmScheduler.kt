package com.codex.appgoodwords.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.codex.appgoodwords.work.TodoAlarmReceiver

/**
 * 할 일 알람을 시각에 맞춰 겁니다.
 *
 * WorkManager를 쓰지 않는 이유는 정확도입니다. WorkManager는 배터리 상태에 따라 수십 분까지
 * 늦출 수 있어서 "9시 회의" 같은 알림에는 쓸 수 없습니다.
 *
 * 대신 Android 12부터 정확한 알람에는 따로 권한이 필요합니다([canScheduleExact]).
 * 권한이 없으면 그 시각 언저리에 오는 알람으로 대신 겁니다. 아예 안 오는 것보다는 낫습니다.
 */
class TodoAlarmScheduler(
    private val context: Context
) {
    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    /** Android 12부터는 사용자가 시스템 설정에서 한 번 켜 줘야 정확한 알람을 걸 수 있습니다. */
    fun canScheduleExact(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }

    /** 사용자를 '알람 및 리마인더' 설정 화면으로 보냅니다. */
    fun exactAlarmSettingsIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** 할 일 하나의 알람을 지금 상태에 맞춥니다. 알람이 없거나 이미 끝냈으면 지웁니다. */
    fun sync(todo: TodoEntity) {
        val remindAt = todo.remindAt
        if (remindAt == null || todo.isDone) {
            cancel(todo)
            return
        }

        // 지난 시각에 걸면 시스템이 즉시 울린다. 이미 지난 알람은 걸지 않는다.
        if (remindAt <= System.currentTimeMillis()) {
            cancel(todo)
            return
        }

        val pendingIntent = pendingIntent(todo, mutable = false) ?: return
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, remindAt, pendingIntent)
        } else {
            // 권한이 없으면 정확도를 포기하고라도 걸어 둔다.
            alarmManager.set(AlarmManager.RTC_WAKEUP, remindAt, pendingIntent)
        }
    }

    fun cancel(todo: TodoEntity) {
        pendingIntent(todo, mutable = false)?.let { alarmManager.cancel(it) }
    }

    /** 기기를 껐다 켜거나 동기화로 할 일이 들어오면 예약이 없으므로 전부 다시 겁니다. */
    fun syncAll(todos: List<TodoEntity>) {
        todos.forEach(::sync)
    }

    private fun pendingIntent(todo: TodoEntity, mutable: Boolean): PendingIntent? {
        val intent = Intent(context, TodoAlarmReceiver::class.java).apply {
            action = TodoAlarmReceiver.ACTION_TODO_ALARM
            putExtra(TodoAlarmReceiver.EXTRA_TODO_ID, todo.id)
            putExtra(TodoAlarmReceiver.EXTRA_TODO_TITLE, todo.title)
            putExtra(TodoAlarmReceiver.EXTRA_TODO_NOTE, todo.note)
            // 같은 할 일의 예약을 덮어쓰려면 인텐트가 같아야 하므로 데이터로 구분한다.
            data = android.net.Uri.parse("appgoodwords://todo/${todo.id}")
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, todo.id.toInt(), intent, flags)
    }
}
