package com.codex.appgoodwords.work

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.codex.appgoodwords.AppGoodWordsApplication
import com.codex.appgoodwords.data.AiFeedbackSettings
import com.codex.appgoodwords.data.GrowthReportEntity
import com.codex.appgoodwords.data.ReminderSettings
import com.codex.appgoodwords.data.ReportPeriod
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 정한 시각에 하루 한 번 오는 것들이 알람으로 실제로 닿는지.
 *
 * 1.18.0까지 일일 집계는 정한 시각에 오지 않았는데 시험은 모두 통과했습니다. 시각을 세는 함수만 보고, 예약이
 * 실제로 언제 도는지는 보지 않았기 때문입니다. 여기서는 진짜 AlarmManager에 몇 초 뒤로 걸어
 * 알람 → 받는 자리([DailyAlarmReceiver]) → 알림까지 지나가는지 봅니다.
 */
class DailyAlarmTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = (context as AppGoodWordsApplication).container
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        // 알림 권한이 없으면 알림 함수가 조용히 돌아갑니다. 주는 것은 괜찮습니다 — 뺏으면 앱 프로세스가 죽습니다.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        reset()
    }

    @After
    fun tearDown() {
        reset()
        // 울린 뒤 받는 자리가 내일 그 시각으로 다시 겁니다. 다른 시험 중에 울리지 않게 걷어 둡니다.
        DailyAlarm.summary(context).cancel()
        DailyAlarm.growth(context).cancel()
    }

    private fun reset() = runBlocking {
        notificationManager.cancel(AppNotifications.dailySummaryNotificationId)
        container.settingsStore.updateSettings(ReminderSettings())
        container.database.growthReportDao().clearAll()
        container.settingsStore.updateAiFeedbackSettings(AiFeedbackSettings())
        container.settingsStore.recordGrowthNudge(0L)
    }

    @Test
    fun theSummaryAlarmPostsTheSummary() {
        DailyAlarm.summary(context).set(System.currentTimeMillis() + 2_000)

        assertTrue("일일 집계 알림이 뜨지 않았습니다.", waitFor { summaryShown() })
    }

    @Test
    fun theGrowthAlarmReachesTheNudge() {
        // 아흐레 전 돌아보기 하나 — 뜸해서 알릴 때입니다([GrowthNudgeWorkerTest]와 같은 기준).
        runBlocking {
            container.database.growthReportDao().insert(
                GrowthReportEntity(
                    syncId = "daily-alarm-test",
                    period = ReportPeriod.WEEKLY.name,
                    guide = "지난 돌아보기",
                    createdAt = System.currentTimeMillis() - 9 * DAY_MILLIS
                )
            )
        }

        DailyAlarm.growth(context).set(System.currentTimeMillis() + 2_000)

        assertTrue(
            "알람이 뜸할 때 알림까지 가지 않았습니다.",
            waitFor { runBlocking { container.settingsStore.getAiFeedbackSettings().lastNudgedAt > 0L } }
        )
    }

    @Test
    fun theOldRepeatingSummaryIsRemoved() {
        // 1.18.0에서 올라온 기기에는 24시간 반복 예약이 남아 있습니다. 남으면 알람과 따로 엉뚱한 때 한 번 더 알립니다.
        val workManager = WorkManager.getInstance(context)
        // 1.18.0이 깔려 있던 기기면 옛 예약이 먼저 돌다가 지운 일꾼(DailySummaryWorker)을 못 찾아 실패로 끝나 있기도
        // 합니다. 그러면 지울 것이 없어 이 시험이 아무것도 보지 못하니, 그 자취를 걷고 새로 걸어 시작합니다.
        workManager.cancelUniqueWork(LEGACY_SUMMARY_WORK_NAME).result.get()
        workManager.pruneWork().result.get()
        workManager.enqueueUniquePeriodicWork(
            LEGACY_SUMMARY_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<GrowthNudgeWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(1, TimeUnit.DAYS)
                .build()
        ).result.get()

        // sync()는 글귀 반복 노출까지 걸어 시험 중에 알림이 뜹니다. 집계 몫만 부릅니다.
        container.reminderScheduler.syncDailySummary(ReminderSettings())

        var states = emptyList<WorkInfo.State>()
        val removed = waitFor {
            states = workManager.getWorkInfosForUniqueWork(LEGACY_SUMMARY_WORK_NAME).get().map { it.state }
            states.isNotEmpty() && states.all { it == WorkInfo.State.CANCELLED }
        }
        assertTrue("옛 반복 예약이 남았습니다: $states", removed)
    }

    private fun summaryShown(): Boolean =
        notificationManager.activeNotifications.any { it.id == AppNotifications.dailySummaryNotificationId }

    private fun waitFor(timeoutMillis: Long = 20_000L, check: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (check()) return true
            Thread.sleep(250)
        }
        return check()
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        const val LEGACY_SUMMARY_WORK_NAME = "good_words_daily_summary"
    }
}
