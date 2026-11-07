package com.codex.appgoodwords.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 하루 한 번, 사용자가 정한 시각에 울리는 알람. 일일 집계와 AI 돌아보기가 씁니다.
 *
 * 1.18.0까지는 WorkManager의 24시간 반복 예약이었는데, 정한 시각을 지키지 못했습니다.
 * - 앱이 켜질 때마다 예약을 다시 걸었습니다(`UPDATE`). WorkManager는 이때 처음 건 시각을 그대로 두고 "남은 시간"만
 *   새로 받습니다. 그래서 한 번 돌기 전에는 앱이 켜질 때마다 그만큼 앞당겨졌습니다.
 * - 한 번 돈 뒤로는 남은 시간을 아예 보지 않고 "지난번 끝난 때부터 24시간 뒤"에 돕니다. 시각을 바꿔도 소용없고,
 *   잠자기로 늦어진 만큼 날마다 뒤로 밀렸습니다.
 *
 * 알람은 그 시각에 울리고, 울릴 때마다 다음 날 그 시각으로 다시 겁니다([DailyAlarmReceiver]).
 * 정확한 시각은 한마디·할 일 알람과 같은 권한을 씁니다([DailyCoachAlarm]). 권한이 없으면 잠자기 중에도 울리되
 * 몇 분 밀릴 수 있는 알람으로 겁니다.
 *
 * **알람마다 하나뿐입니다.** 다시 걸면 앞의 것을 덮어써서, 앱·설정·알람이 저마다 걸어도 겹쳐 울리지 않습니다.
 */
class DailyAlarm private constructor(
    private val context: Context,
    private val action: String,
    private val requestCode: Int
) {
    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    fun set(triggerAtMillis: Long) {
        val operation = pendingIntent()
        val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canScheduleExact) {
            // 확인한 뒤 걸기 전에 권한이 거둬질 수 있습니다. 그때는 늦어도 울리는 쪽으로 겁니다.
            val exact = runCatching {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            }
            if (exact.isSuccess) return
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
    }

    fun cancel() {
        alarmManager.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, DailyAlarmReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        /** 일일 집계. [com.codex.appgoodwords.data.ReminderSettings.summaryHour]에 울립니다. */
        fun summary(context: Context): DailyAlarm =
            DailyAlarm(context, DailyAlarmReceiver.ACTION_DAILY_SUMMARY, 7_020)

        /**
         * AI 돌아보기 자동 실행과 뜸할 때 알림. 둘은 같은 시각을 씁니다
         * ([com.codex.appgoodwords.data.AiFeedbackSettings.hour]).
         */
        fun growth(context: Context): DailyAlarm =
            DailyAlarm(context, DailyAlarmReceiver.ACTION_GROWTH, 7_021)
    }
}
