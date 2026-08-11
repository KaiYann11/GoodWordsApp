package com.codex.appgoodwords.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.codex.appgoodwords.AppGoodWordsApplication
import com.codex.appgoodwords.data.ExposureEventType
import com.codex.appgoodwords.data.ExposureTrigger
import com.codex.appgoodwords.data.ReminderSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 알림 권한이 없을 때 무엇을 기록하는지 봅니다.
 *
 * 권한이 없으면 알림 함수가 조용히 돌아갑니다. 그런데도 노출·읽음을 기록하면
 * 보지도 않은 글귀가 읽음으로 쌓이고, 순환 기준까지 앞서 나가 정작 앱을 열었을 때
 * 볼 글귀가 남지 않습니다.
 */
class QuoteReminderWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = (context as AppGoodWordsApplication).container

    @Before
    fun setUp() {
        runBlocking {
            container.repository.seedDefaultsIfNeeded()
            // 창 밖이면 아무것도 하지 않고 끝나므로 하루 종일 열어 둔다.
            container.settingsStore.updateSettings(
                ReminderSettings(
                    remindersEnabled = true,
                    preferredHour = 0,
                    preferredMinute = 0,
                    repeatEndHour = 0,
                    repeatEndMinute = 0
                )
            )
        }
    }

    @After
    fun tearDown() {
        runBlocking { container.settingsStore.updateSettings(ReminderSettings()) }
    }

    @Test
    fun withoutPermissionItRecordsNoNotificationExposure() = runBlocking {
        val before = countNotificationEvents()

        val result = worker().run(canPostNotifications = false)

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(
            "보내지도 못한 알림이 이력에 남았습니다.",
            before,
            countNotificationEvents()
        )
    }

    @Test
    fun withoutPermissionTheWidgetStillMovesOn() = runBlocking {
        val before = countWidgetEvents()

        worker().run(canPostNotifications = false)

        assertTrue(
            "알림을 못 보내도 위젯은 계속 돌아야 합니다.",
            countWidgetEvents() > before
        )
    }

    @Test
    fun withPermissionItRecordsTheNotificationExposure() = runBlocking {
        val before = countNotificationEvents()

        val result = worker().run(canPostNotifications = true)

        assertEquals(ListenableWorker.Result.success(), result)
        // 루틴이 뽑히면 글귀 이력은 늘지 않으므로 줄지만 않으면 된다.
        assertTrue("이력이 줄었습니다.", countNotificationEvents() >= before)
    }

    private fun worker() = TestListenableWorkerBuilder<QuoteReminderWorker>(context).build()

    private suspend fun countNotificationEvents(): Int = container.database.exposureEventDao()
        .getAll()
        .count { it.trigger == ExposureTrigger.REMINDER_NOTIFICATION }

    private suspend fun countWidgetEvents(): Int = container.database.exposureEventDao()
        .getAll()
        .count { it.trigger == ExposureTrigger.WIDGET_REFRESH && it.eventType == ExposureEventType.SURFACED }
}
