package com.codex.appgoodwords.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.codex.appgoodwords.data.AppDatabase
import com.codex.appgoodwords.data.RoutineAlarms
import com.codex.appgoodwords.data.RoutineCheckEntity
import com.codex.appgoodwords.data.RoutineEntity
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine

/**
 * 시각을 정한 루틴의 알람(사용자가 정함, 2026-11-17). 언제 울릴지는 [RoutineAlarms]가 셉니다.
 *
 * **알람은 하나뿐입니다** — 모든 루틴을 통틀어 가장 가까운 한 때에 걸고, 울리면 그때까지 지난 것을 알리고 다음 것을 다시
 * 겁니다([RoutineAlarmReceiver]). 다시 걸면 앞의 것을 덮어쓰므로 앱·알람·재부팅이 저마다 걸어도 겹쳐 울리지 않습니다.
 * 정확한 시각은 할 일·한마디 알람과 같은 권한을 씁니다. 권한이 없으면 몇 분 밀릴 수 있는 알람으로 겁니다.
 */
class RoutineAlarm(
    private val context: Context
) {
    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    /**
     * 루틴이나 체크가 바뀔 때마다 다시 겁니다(앱이 떠 있는 동안 — [com.codex.appgoodwords.AppGoodWordsApplication]).
     *
     * 앱·위젯·알림에서 체크하거나, 동기화·가져오기로 루틴이 바뀌어도 여기서 한 번에 잡습니다. 고치는 자리마다 부르면 하나를
     * 빠뜨리기 쉽고, 빠뜨린 자리에서는 이미 한 루틴이 한 시간 뒤에 또 울립니다.
     */
    suspend fun watch(database: AppDatabase) {
        // 오늘 체크했는지만 봅니다. 하루 넉넉히 앞에서부터 읽습니다(앱이 자정을 넘겨 떠 있어도 오늘이 빠지지 않게).
        val since = LocalDate.now().minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        combine(database.routineDao().observeAll(), database.routineCheckDao().observeSince(since)) { routines, checks ->
            routines to checks
        }
            // 앱 전체에 걸린 흐름이라 여기서 던지면 앱이 죽습니다. 알람 하나를 못 거는 편이 낫습니다.
            .catch { error -> Log.w(TAG, "루틴 알람을 지켜보지 못했습니다.", error) }
            .collect { (routines, checks) ->
                runCatching { sync(routines, checks) }.onFailure { Log.w(TAG, "루틴 알람을 걸지 못했습니다.", it) }
            }
    }

    /** 지금 상태에 맞춰 다음 알람을 겁니다. 시각을 정한 루틴이 없으면 지웁니다. 그날 체크한 루틴의 알림은 거둡니다. */
    fun sync(
        routines: List<RoutineEntity>,
        checks: List<RoutineCheckEntity>,
        now: Long = System.currentTimeMillis()
    ) {
        val zoneId = ZoneId.systemDefault()
        clearSettled(routines, checks, zoneId)
        val next = RoutineAlarms.nextAt(routines, checks, now, zoneId)
        if (next == null) cancel() else set(next)
    }

    /**
     * 알람이 울린 참에 [plannedAt]부터 지금까지 지난 것을 알리고 다음 것을 겁니다.
     *
     * 여럿이 한꺼번에 울려도 소리는 한 번입니다 — 첫 알림만 소리를 내고 나머지는 조용히 놓입니다.
     */
    fun fire(
        routines: List<RoutineEntity>,
        checks: List<RoutineCheckEntity>,
        plannedAt: Long,
        now: Long = System.currentTimeMillis()
    ) {
        val zoneId = ZoneId.systemDefault()
        RoutineAlarms.due(routines, checks, plannedAt, now, zoneId).forEachIndexed { index, alarm ->
            AppNotifications.showRoutineAlarmNotification(context, alarm, silent = index > 0, now = now)
        }
        sync(routines, checks, now)
    }

    fun cancel() {
        alarmManager.cancel(pendingIntent(0L))
    }

    private fun set(triggerAtMillis: Long) {
        val operation = pendingIntent(triggerAtMillis)
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

    /**
     * 그날 체크했거나, 상시로 바뀌었거나, 지운 루틴의 알림은 더 볼 일이 없습니다.
     *
     * 알림은 그 루틴을 하는 날로 가려 둡니다([AppNotifications.showRoutineAlarmNotification]). 오늘 체크한 것이 오늘 밤에 온
     * 내일 몫의 30분 전 알림(00:10 루틴)까지 거두면 안 됩니다.
     */
    private fun clearSettled(
        routines: List<RoutineEntity>,
        checks: List<RoutineCheckEntity>,
        zoneId: ZoneId
    ) {
        val byId = routines.associateBy { it.id }
        val doneByDate = mutableMapOf<LocalDate, Set<Long>>()
        AppNotifications.clearRoutineAlarmNotifications(context) { routineId, date ->
            val routine = byId[routineId]
            routine?.timeOfDay != null &&
                routineId !in doneByDate.getOrPut(date) { RoutineAlarms.doneOn(routines, checks, date, zoneId) }
        }
    }

    /** 울릴 때를 함께 실어 보냅니다. 울린 쪽은 그때부터 지금까지 지난 것을 알립니다([RoutineAlarmReceiver]). */
    private fun pendingIntent(plannedAt: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, RoutineAlarmReceiver::class.java)
            .setAction(RoutineAlarmReceiver.ACTION_ROUTINE_ALARM)
            .putExtra(RoutineAlarmReceiver.EXTRA_PLANNED_AT, plannedAt),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private companion object {
        /** [DailyAlarm]의 7_020·7_021 옆입니다. */
        const val REQUEST_CODE = 7_030
        const val TAG = "RoutineAlarm"
    }
}
