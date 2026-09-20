package com.codex.appgoodwords.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.codex.appgoodwords.AppGoodWordsApplication
import com.codex.appgoodwords.data.ServerSyncSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 배경 동기화가 실제로 도는지 확인합니다.
 *
 * 화면 없이 도는 경로라 눌러 볼 수가 없고, 조용히 실패하면 사용자는 동기화가 되고 있다고 믿습니다.
 *
 * 서버가 떠 있지 않으면 병합 테스트는 건너뜁니다.
 * testServerUrl에는 운영 데이터가 없는 별도 HTTPS 테스트 서버를 지정합니다.
 */
class SyncWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = (context as AppGoodWordsApplication).container

    // 테스트할 때만 전달하며 운영 서버를 사용하지 않습니다.
    private val serverUrl = InstrumentationRegistry.getArguments().getString("testServerUrl").orEmpty()
    private val serverApiKey = InstrumentationRegistry.getArguments().getString("testServerApiKey").orEmpty()

    @After
    fun tearDown() = runBlocking {
        container.settingsStore.updateServerSyncSettings(ServerSyncSettings())
    }

    @Test
    fun worker_doesNothingWhenAutoSyncIsOff() = runBlocking {
        container.settingsStore.updateServerSyncSettings(
            ServerSyncSettings(serverUrl = serverUrl, autoSyncEnabled = false)
        )
        container.settingsStore.recordSyncResult(0L, error = "")

        val result = TestListenableWorkerBuilder<SyncWorker>(context).build().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertFalse(
            "꺼져 있는데 동기화가 돌았습니다.",
            container.settingsStore.syncStatusFlow.first().hasSynced
        )
    }

    @Test
    fun worker_doesNothingWithoutAServerAddress() = runBlocking {
        container.settingsStore.updateServerSyncSettings(
            ServerSyncSettings(serverUrl = "", autoSyncEnabled = true)
        )
        container.settingsStore.recordSyncResult(0L, error = "")

        val result = TestListenableWorkerBuilder<SyncWorker>(context).build().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertFalse(container.settingsStore.syncStatusFlow.first().hasSynced)
    }

    @Test
    fun worker_recordsFailureSoTheUserCanSeeIt() = runBlocking {
        container.settingsStore.updateServerSyncSettings(
            // 아무도 듣고 있지 않은 포트.
            ServerSyncSettings(serverUrl = "http://10.0.2.2:1", autoSyncEnabled = true)
        )

        val result = TestListenableWorkerBuilder<SyncWorker>(context).build().doWork()

        assertEquals("서버가 잠깐 꺼진 것일 수 있으니 다시 시도해야 합니다.", ListenableWorker.Result.retry(), result)
        val status = container.settingsStore.syncStatusFlow.first()
        assertTrue("실패가 남지 않으면 사용자는 이유를 알 수 없습니다.", status.failed)
    }

    @Test
    fun worker_mergesWithServerAndRecordsSuccess() = runBlocking {
        assumeTrue("testServerUrl에 별도 HTTPS 테스트 서버를 지정해 주세요.", serverUrl.isNotBlank())
        container.serverSyncClient.testConnection(ServerSyncSettings(serverUrl = serverUrl, apiKey = serverApiKey))
        container.settingsStore.updateServerSyncSettings(
            ServerSyncSettings(serverUrl = serverUrl, apiKey = serverApiKey, autoSyncEnabled = true)
        )

        val result = TestListenableWorkerBuilder<SyncWorker>(context).build().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        val status = container.settingsStore.syncStatusFlow.first()
        assertTrue(status.hasSynced)
        assertFalse("실패가 남아 있습니다: ${status.lastError}", status.failed)
    }

}
