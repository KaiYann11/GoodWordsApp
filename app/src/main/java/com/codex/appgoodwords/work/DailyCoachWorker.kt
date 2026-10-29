package com.codex.appgoodwords.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.codex.appgoodwords.AppGoodWordsApplication
import java.time.Instant
import java.time.ZoneId

/**
 * 앱을 닫아 두어도 주기마다 오늘의 한마디를 받아 알립니다.
 *
 * 예전에는 홈을 열어 둔 동안에만 물었습니다(값이 드는 일이 몰래 돌지 않게). 그랬더니 앱을 열지 않는 날에는
 * 한마디가 오지 않아, 켜 두고도 있는 줄 몰랐습니다. 사용자가 정해 배경에서도 돕니다(2026-10-19) —
 * 대신 **설정에 스위치로 보이고**([com.codex.appgoodwords.data.AiFeedbackSettings.coachBackground]),
 * 묻는 규칙은 홈과 같습니다([com.codex.appgoodwords.data.DailyCoachCoordinator.refresh]): 주기와 하루 횟수,
 * 오늘 한 것이 하나도 없으면 묻지 않음.
 *
 * **보고 있는 화면 위에 알림을 겹쳐 띄우지 않습니다.** 앱이 앞에 떠 있으면 카드와 홈 탭의 점이 알립니다.
 *
 * **알람이 깨웁니다**([DailyCoachAlarm]). 예전에는 WorkManager에 "주기 뒤"로 미뤄 걸었는데, 휴대폰을 내려놓으면
 * 안드로이드가 몇 시간씩 늦춰서 앱을 열어야만 한마디가 왔습니다. 홈에서 묻던 중에 앱을 나가 끊긴 것도 넘겨받아
 * 이어 묻습니다([ReminderScheduler.handOverDailyCoach]). 내 말에 답을 기다리다 끊긴 것도 같습니다.
 *
 * 한 번 돌고 나면 다음 알람을 겁니다 — 마지막으로 물은 때부터 한 주기 뒤.
 */
class DailyCoachWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as AppGoodWordsApplication
        val container = app.container
        val aiSettings = container.settingsStore.getAiFeedbackSettings()
        if (!aiSettings.coachRunsInBackground) return Result.success()

        val startedAt = System.currentTimeMillis()
        val reminderSettings = container.settingsStore.getSettings()
        // 답을 기다리다 화면을 떠나 끊긴 내 말이 있으면 그 답부터 받습니다. 내가 걸어 둔 말이라 주기·하루 횟수를 보지 않습니다.
        if (container.dailyCoachCoordinator.state().replyInterrupted) {
            val talked = container.dailyCoachCoordinator.retryReply()
            val answer = talked.answerSince(startedAt)
            if (answer != null && !app.mainScreenVisible) {
                AppNotifications.showDailyCoachAnswerNotification(
                    context = applicationContext,
                    reply = talked.replyBefore(answer),
                    answer = answer,
                    soundEnabled = reminderSettings.notificationSoundEnabled,
                    lockScreenVisible = reminderSettings.lockScreenVisible
                )
            }
        }

        // 방금 말을 주고받았으면 대개 물을 때가 아닙니다 — 다음 한마디는 내가 말한 때부터 한 주기 뒤입니다.
        val state = container.dailyCoachCoordinator.refresh(now = startedAt)
        val tip = state.tip
        // 이번에 받은 것만 알립니다. 홈이 묻는 동안 기다렸다 들여다본 것이면 그 한마디는 홈이 받은 것입니다.
        if (tip != null && tip.createdAt >= startedAt && !app.mainScreenVisible) {
            AppNotifications.showDailyCoachNotification(
                context = applicationContext,
                tip = tip,
                soundEnabled = reminderSettings.notificationSoundEnabled,
                lockScreenVisible = reminderSettings.lockScreenVisible
            )
        }

        // 물었든 아니든(오늘 한 것이 없거나 하루 횟수를 다 썼어도) 다음 차례를 겁니다. 알람은 하나뿐이라
        // 홈에서 끊긴 것을 넘겨받은 한 번이 걸어도 겹치지 않고 덮어씁니다.
        container.reminderScheduler.scheduleNextDailyCoach(aiSettings, state)
        return Result.success()
    }
}

/**
 * 다음에 깨울 때까지의 시간.
 *
 * 순수 함수로 둡니다. 시각을 받아야 기기 없이 검증할 수 있습니다.
 */
object DailyCoachSchedule {
    /** 가장 이르게 깨우는 때. 앱이 막 열리며 홈이 직접 묻는 것과 겹치지 않게 조금 비웁니다. */
    const val MIN_DELAY_MILLIS = 60_000L

    /**
     * 앱을 켜거나 설정을 바꿀 때 걸 예약의 시간.
     *
     * **마지막으로 물은 때부터 주기를 셉니다.** 앱을 켤 때마다 주기를 처음부터 세면, 앱을 자주 여는 날일수록
     * 배경 한마디가 밀려 끝내 오지 않습니다. 오늘 아직 묻지 않았으면 곧 깨웁니다.
     */
    fun delayMillis(gapMinutes: Long, lastAskedAt: Long, now: Long): Long {
        val gap = gapMinutes * 60_000L
        if (lastAskedAt <= 0L) return MIN_DELAY_MILLIS
        return (lastAskedAt + gap - now).coerceIn(MIN_DELAY_MILLIS, gap.coerceAtLeast(MIN_DELAY_MILLIS))
    }

    /**
     * 일꾼이 한 번 돈 뒤 다음에 깨울 때까지.
     *
     * 마지막으로 물은 때부터 한 주기 뒤입니다. 물을 때가 지났는데도 묻지 않았으면 — 오늘 한 것이 아직 없으면 —
     * 지금부터 한 주기 뒤에 다시 봅니다. [delayMillis]처럼 곧 깨우면 같은 것을 보고 그냥 돌아가기를 1분마다 되풀이합니다.
     */
    fun nextDelayMillis(gapMinutes: Long, lastAskedAt: Long, now: Long): Long {
        val gap = gapMinutes * 60_000L
        val due = lastAskedAt + gap - now
        return if (lastAskedAt > 0L && due > 0L) due.coerceIn(MIN_DELAY_MILLIS, gap) else gap
    }

    /**
     * 하루 몫을 다 썼을 때 새 하루가 열릴 때까지.
     *
     * 자정이 지나면 횟수가 비지만 그때는 오늘 한 것도 비어 있어 묻지 않습니다. 그래도 그 뒤로는 주기마다 다시
     * 들여다봐야 첫 걸음을 밟은 뒤에 한마디가 옵니다.
     */
    fun untilTomorrowMillis(now: Long, zoneId: ZoneId): Long {
        val tomorrow = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate().plusDays(1)
        val midnight = tomorrow.atStartOfDay(zoneId).toInstant().toEpochMilli()
        return (midnight - now).coerceAtLeast(MIN_DELAY_MILLIS)
    }

    /**
     * 다음 한마디가 저절로 올 때. 홈 카드가 "다음 한마디 15:20쯤"으로 보여 줍니다.
     *
     * 오늘 아직 묻지 않았거나(무엇을 해야 오는지는 카드가 따로 말합니다) 하루 몫을 다 썼으면 null입니다.
     * 남은 횟수는 세어 보이지 않습니다 — 다음 하나만 말합니다.
     */
    fun nextAskAt(gapMinutes: Long, limit: Int, askCount: Int, lastAskedAt: Long): Long? =
        if (limit <= 0 || askCount >= limit || lastAskedAt <= 0L) null else lastAskedAt + gapMinutes * 60_000L
}
