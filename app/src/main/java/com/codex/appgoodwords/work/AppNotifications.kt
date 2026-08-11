package com.codex.appgoodwords.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.codex.appgoodwords.MainActivity
import com.codex.appgoodwords.R
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.DailySummary
import com.codex.appgoodwords.data.ReminderSettings
import com.codex.appgoodwords.data.RoutineEntity

object AppNotifications {
    const val contentChannelId = "daily_good_words_sound"
    const val contentSilentChannelId = "daily_good_words_silent"
    const val summaryChannelId = "daily_good_words_summary_sound"
    const val summarySilentChannelId = "daily_good_words_summary_silent"
    const val todoChannelId = "good_words_todo_alarm"
    const val actionMarkRead = "com.codex.appgoodwords.action.MARK_READ"
    const val actionCheckRoutine = "com.codex.appgoodwords.action.CHECK_ROUTINE"
    const val extraContentId = "extra_content_id"
    const val extraRoutineId = "extra_routine_id"
    const val extraMarkConfirmed = "extra_mark_confirmed"
    const val extraRecordView = "extra_record_view"
    const val extraNotificationId = "extra_notification_id"
    const val extraTodoId = "extra_todo_id"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java)

        val contentChannel = NotificationChannel(
            contentChannelId,
            "${context.getString(R.string.app_name)} 알림",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "명언과 저장한 링크를 주기적으로 보여주는 알림"
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }

        val contentSilentChannel = NotificationChannel(
            contentSilentChannelId,
            "${context.getString(R.string.app_name)} 조용한 알림",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "소리 없이 표시만 하는 알림"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }

        val summaryChannel = NotificationChannel(
            summaryChannelId,
            "${context.getString(R.string.app_name)} 일일 집계",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "하루 동안 조회한 항목과 읽은 항목 집계"
        }

        val summarySilentChannel = NotificationChannel(
            summarySilentChannelId,
            "${context.getString(R.string.app_name)} 조용한 일일 집계",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "소리 없이 표시만 하는 일일 집계 알림"
            setSound(null, null)
            enableVibration(false)
        }

        // 할 일 알람은 사용자가 시각을 직접 정한 것이라 소리 설정과 무관하게 높게 둡니다.
        val todoChannel = NotificationChannel(
            todoChannelId,
            "${context.getString(R.string.app_name)} 할 일 알람",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "정한 시각에 할 일을 알려주는 알람"
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }

        manager.createNotificationChannel(contentChannel)
        manager.createNotificationChannel(contentSilentChannel)
        manager.createNotificationChannel(summaryChannel)
        manager.createNotificationChannel(summarySilentChannel)
        manager.createNotificationChannel(todoChannel)
    }

    fun showTodoNotification(
        context: Context,
        todoId: Long,
        title: String,
        note: String
    ) {
        if (!canPostNotifications(context)) return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(extraTodoId, todoId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            todoNotificationId(todoId),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, todoChannelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("할 일: $title")
            .setContentText(note.ifBlank { "정한 시각이 되었습니다." })
            .setStyle(NotificationCompat.BigTextStyle().bigText(note.ifBlank { title }))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(todoNotificationId(todoId), notification)
    }

    /** 글귀 알림은 항목 id를 그대로 쓰므로, 할 일과 겹치지 않게 따로 떨어뜨립니다. */
    private fun todoNotificationId(todoId: Long): Int = (todoId % 10_000L).toInt() + 20_000

    fun showContentNotification(
        context: Context,
        item: ContentItemEntity,
        settings: ReminderSettings,
        countedAsViewedInNotification: Boolean = false
    ) {
        if (!hasNotificationPermission(context)) return

        val notificationId = item.id.toInt().coerceAtLeast(1)
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(extraContentId, item.id)
            putExtra(extraMarkConfirmed, false)
            putExtra(extraRecordView, !countedAsViewedInNotification)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val markReadIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = actionMarkRead
            putExtra(extraContentId, item.id)
            putExtra(extraRecordView, !countedAsViewedInNotification)
            putExtra(extraNotificationId, notificationId)
        }

        val markReadPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 50_000,
            markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = item.body.ifBlank {
            item.sourceUrl.ifBlank { "다시 확인할 내용을 준비해 두었어요." }
        }
        val title = item.title.ifBlank {
            if (item.author.isNotBlank()) "${item.author}의 글귀" else "오늘의 글귀"
        }
        val expandedText = buildString {
            append(contentText)
            if (item.author.isNotBlank()) {
                append("\n\n- ")
                append(item.author)
            }
            if (item.sourceUrl.isNotBlank()) {
                append("\n")
                append(item.sourceUrl)
            }
        }

        val notification = NotificationCompat.Builder(context, contentChannelFor(settings))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(expandedText)
                    .setSummaryText(item.category.ifBlank { item.author.ifBlank { "오늘의 글귀" } })
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(
                if (settings.lockScreenVisible) {
                    NotificationCompat.VISIBILITY_PUBLIC
                } else {
                    NotificationCompat.VISIBILITY_PRIVATE
                }
            )
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.checkbox_on_background,
                "읽음 처리",
                markReadPendingIntent
            )
            .setAutoCancel(true)
            .setSilent(!settings.notificationSoundEnabled)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun showRoutineNotification(
        context: Context,
        routine: RoutineEntity,
        todayCount: Int,
        settings: ReminderSettings
    ) {
        if (!hasNotificationPermission(context)) return

        val notificationId = (routine.id.toInt() + 200_000).coerceAtLeast(200_001)
        val contentIntent = Intent(context, MainActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val checkIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = actionCheckRoutine
            putExtra(extraRoutineId, routine.id)
            putExtra(extraNotificationId, notificationId)
        }

        val checkPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 50_000,
            checkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = routine.note.ifBlank { "오늘 ${todayCount}회 수행했습니다." }
        val expandedText = buildString {
            append(contentText)
            append("\n\n오늘 수행: ")
            append(todayCount)
            append("회")
            if (routine.category.isNotBlank()) {
                append("\n")
                append(routine.category)
            }
        }

        val notification = NotificationCompat.Builder(context, contentChannelFor(settings))
            .setSmallIcon(android.R.drawable.checkbox_on_background)
            .setContentTitle(routine.title)
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(expandedText)
                    .setSummaryText("루틴")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(
                if (settings.lockScreenVisible) {
                    NotificationCompat.VISIBILITY_PUBLIC
                } else {
                    NotificationCompat.VISIBILITY_PRIVATE
                }
            )
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.checkbox_on_background,
                "수행 +1",
                checkPendingIntent
            )
            .setAutoCancel(true)
            .setSilent(!settings.notificationSoundEnabled)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun showDailySummaryNotification(
        context: Context,
        summary: DailySummary,
        soundEnabled: Boolean
    ) {
        if (!hasNotificationPermission(context)) return

        val openAppIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            9_001,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val style = NotificationCompat.InboxStyle()
            .setSummaryText("조회 ${summary.totalShown}건 / 읽음 ${summary.totalConfirmed}건")

        if (summary.shownItems.isEmpty()) {
            style.addLine("조회: 없음")
        } else {
            style.addLine("조회 목록")
            summary.shownItems.take(4).forEach { line ->
                style.addLine("- ${line.title}${formatCount(line.count)}")
            }
        }

        if (summary.confirmedItems.isEmpty()) {
            style.addLine("읽음: 없음")
        } else {
            style.addLine("읽음 목록")
            summary.confirmedItems.take(4).forEach { line ->
                style.addLine("- ${line.title}${formatCount(line.count)}")
            }
        }

        val summaryText = "조회 ${summary.totalShown}건 / 읽음 ${summary.totalConfirmed}건"
        val notification = NotificationCompat.Builder(context, summaryChannelFor(soundEnabled))
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("오늘의 조회/읽음 집계")
            .setContentText(summaryText)
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSilent(!soundEnabled)
            .build()

        NotificationManagerCompat.from(context).notify(9_002, notification)
    }

    private fun contentChannelFor(settings: ReminderSettings): String {
        return if (settings.notificationSoundEnabled) contentChannelId else contentSilentChannelId
    }

    private fun summaryChannelFor(soundEnabled: Boolean): String {
        return if (soundEnabled) summaryChannelId else summarySilentChannelId
    }

    /**
     * 알림을 보낼 수 있는지 확인합니다.
     *
     * 권한이 없으면 알림 함수들은 조용히 돌아가므로, 부르는 쪽이 먼저 물어야
     * 보내지도 못한 알림을 보낸 것처럼 기록하지 않습니다.
     */
    fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotificationPermission(context: Context): Boolean = canPostNotifications(context)

    private fun formatCount(count: Int): String = if (count > 1) " (${count}회)" else ""
}
