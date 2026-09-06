package com.codex.appgoodwords.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.codex.appgoodwords.AppGoodWordsApplication
import com.codex.appgoodwords.data.AiFeedbackSettings
import com.codex.appgoodwords.data.GrowthCadence
import com.codex.appgoodwords.data.GrowthReportEntity
import com.codex.appgoodwords.data.ReportPeriod
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 오래 돌아보지 않았을 때 알리는 배경 작업입니다.
 *
 * 화면 없이 도는 경로라 눌러 볼 수가 없습니다. 조용히 안 돌면 사용자는 "알려 주겠지" 하고
 * 믿은 채로 몇 달을 지나갑니다. 반대로 너무 자주 돌면 잔소리가 됩니다.
 *
 * 알림이 실제로 떴는지는 여기서 볼 수 없어(권한과 시스템 몫입니다) **알린 자취**로 봅니다 —
 * 알리고 나면 `lastNudgedAt`을 적어 두므로, 그 값이 곧 "알렸다"는 뜻입니다.
 */
class GrowthNudgeWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = (context as AppGoodWordsApplication).container

    @Before
    fun setUp() = reset()

    @After
    fun tearDown() = reset()

    private fun reset() = runBlocking {
        container.database.growthReportDao().clearAll()
        container.settingsStore.updateAiFeedbackSettings(AiFeedbackSettings())
        container.settingsStore.recordGrowthNudge(0L)
    }

    private suspend fun lastNudgedAt(): Long =
        container.settingsStore.getAiFeedbackSettings().lastNudgedAt

    private suspend fun runWorker(): ListenableWorker.Result =
        TestListenableWorkerBuilder<GrowthNudgeWorker>(context).build().doWork()

    private suspend fun keepReport(daysAgo: Long) {
        container.database.growthReportDao().insert(
            GrowthReportEntity(
                syncId = "nudge-test-$daysAgo",
                period = ReportPeriod.WEEKLY.name,
                guide = "지난 돌아보기",
                createdAt = System.currentTimeMillis() - daysAgo * DAY_MILLIS
            )
        )
    }

    @Test
    fun itSpeaksWhenAWeekHasPassed() = runBlocking {
        keepReport(daysAgo = 9)

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue("알리지 않았습니다.", lastNudgedAt() > 0L)
    }

    @Test
    fun itStaysQuietWhenTheReviewIsRecent() = runBlocking {
        keepReport(daysAgo = 2)

        runWorker()

        assertEquals("아직 뜸하지 않은데 알렸습니다.", 0L, lastNudgedAt())
    }

    @Test
    fun itStaysQuietWhenThereIsNoReviewYet() = runBlocking {
        // 앱을 막 깐 사람에게 첫날부터 "안 했다"고 알리면 그저 성가십니다.
        runWorker()

        assertEquals(0L, lastNudgedAt())
    }

    @Test
    fun itDoesNotNagEveryDay() = runBlocking {
        keepReport(daysAgo = 9)
        runWorker()
        val firstNudge = lastNudgedAt()

        // 바로 다음 날 또 깨어나도 다시 알리면 안 됩니다.
        runWorker()

        assertEquals("이틀 내리 알렸습니다.", firstNudge, lastNudgedAt())
    }

    @Test
    fun turningItOffKeepsItSilent() = runBlocking {
        keepReport(daysAgo = 30)
        container.settingsStore.updateAiFeedbackSettings(
            AiFeedbackSettings(nudgeEnabled = false)
        )

        runWorker()

        assertEquals(0L, lastNudgedAt())
    }

    @Test
    fun theThresholdMatchesWhatTheScreenSays() {
        // 화면과 알림이 다른 날수를 말하면 사용자가 앱을 못 믿게 됩니다.
        assertEquals(7L, GrowthCadence.OVERDUE_DAYS)
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
