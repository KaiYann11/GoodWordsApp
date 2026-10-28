package com.codex.appgoodwords.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 오늘의 한마디를 정한 주기에 깨우는 알람.
 *
 * 1.11.1까지는 WorkManager에 "몇 분 뒤"로 미뤄 걸었습니다. 휴대폰을 내려놓으면 안드로이드가 그런 예약을
 * 몇 시간씩 늦춰서(잠자기·앱 대기 버킷) 앱을 열어야만 한마디가 왔습니다. 알람은 잠자기 중에도 그 시각에
 * 울립니다. 울리면 곧바로 도는 일꾼에게 넘깁니다([DailyCoachAlarmReceiver]) — 묻는 데 1~2분이 걸리기도 하는데
 * 알람을 받는 자리는 몇 초 만에 닫힙니다.
 *
 * **알람은 하나뿐입니다.** 다시 걸면 앞의 것을 덮어씁니다. 그래서 일꾼·앱·설정이 저마다 다음 차례를 걸어도
 * 겹쳐 울리지 않습니다.
 *
 * 정확한 시각은 할 일 알람과 같은 권한을 씁니다([com.codex.appgoodwords.data.TodoAlarmScheduler]). 권한이 없으면
 * 잠자기 중에도 울리되 몇 분 밀릴 수 있는 알람으로 겁니다. 아예 안 오는 것보다 낫습니다.
 */
class DailyCoachAlarm(
    private val context: Context
) {
    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    /** Android 12부터는 사용자가 시스템 설정에서 한 번 켜 줘야 정확한 알람을 걸 수 있습니다. */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun set(triggerAtMillis: Long) {
        val operation = pendingIntent()
        if (canScheduleExact()) {
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
        REQUEST_CODE,
        Intent(context, DailyCoachAlarmReceiver::class.java).setAction(DailyCoachAlarmReceiver.ACTION_DAILY_COACH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private companion object {
        const val REQUEST_CODE = 7_010
    }
}
