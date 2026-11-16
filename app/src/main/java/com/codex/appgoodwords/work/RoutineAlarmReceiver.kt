package com.codex.appgoodwords.work

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.codex.appgoodwords.AppGoodWordsApplication
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 루틴 알람([RoutineAlarm])이 울릴 때와, 알람을 다시 걸어야 할 때 불립니다.
 *
 * - **울리면**: 걸어 둔 때부터 지금까지 지난 것을 알리고 다음 것을 겁니다. DB만 읽어 금방 끝납니다.
 * - **기기를 다시 켜거나 앱이 새로 깔리면**: AlarmManager 예약은 그때 사라지므로 다시 겁니다.
 * - **정확한 알람 권한을 켜면**: 그때부터 정확한 시각으로 다시 겁니다.
 * - **시계나 시간대가 바뀌면**: 알람은 절대 시각으로 걸려 있어, 그대로 두면 정한 시각과 어긋납니다.
 */
class RoutineAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val application = context.applicationContext as? AppGoodWordsApplication ?: return
        val action = intent.action
        if (action != ACTION_ROUTINE_ALARM && action !in RESYNC_ACTIONS) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = application.container
                val now = System.currentTimeMillis()
                val routines = container.database.routineDao().getAll()
                // 오늘 체크했는지만 봅니다(어제 0시부터 — 자정 언저리에 울려도 그날이 빠지지 않게).
                val since = LocalDate.now().minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val checks = container.database.routineCheckDao().getSince(since)
                if (action == ACTION_ROUTINE_ALARM) {
                    // 걸어 둔 때가 실려 오지 않았으면(옛 예약) 방금 지난 것만 봅니다.
                    val plannedAt = intent.getLongExtra(EXTRA_PLANNED_AT, 0L).takeIf { it > 0L } ?: (now - 60_000L)
                    container.routineAlarm.fire(routines, checks, plannedAt, now)
                } else {
                    container.routineAlarm.sync(routines, checks, now)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ROUTINE_ALARM = "com.codex.appgoodwords.action.ROUTINE_ALARM"
        const val EXTRA_PLANNED_AT = "planned_at"

        private val RESYNC_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )
    }
}
