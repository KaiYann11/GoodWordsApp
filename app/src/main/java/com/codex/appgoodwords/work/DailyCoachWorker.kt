package com.codex.appgoodwords.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.codex.appgoodwords.AppGoodWordsApplication

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
 * 한 번 돌고 나면 다음 차례를 겁니다. 주기 작업은 안드로이드가 15분보다 촘촘히 돌리지 않아 10분 주기를
 * 지킬 수 없습니다.
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

        val before = container.dailyCoachCoordinator.state()
        val after = container.dailyCoachCoordinator.refresh()
        val tip = after.tip
        if (tip != null && tip.createdAt != before.tip?.createdAt && !app.mainScreenVisible) {
            val reminderSettings = container.settingsStore.getSettings()
            AppNotifications.showDailyCoachNotification(
                context = applicationContext,
                tip = tip,
                soundEnabled = reminderSettings.notificationSoundEnabled,
                lockScreenVisible = reminderSettings.lockScreenVisible
            )
        }

        // 물었든 아니든(오늘 한 것이 없거나 하루 횟수를 다 썼어도) 한 주기 뒤에 다시 봅니다.
        container.reminderScheduler.scheduleNextDailyCoach(aiSettings)
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
}
