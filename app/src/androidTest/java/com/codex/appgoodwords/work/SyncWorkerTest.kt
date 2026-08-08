package com.codex.appgoodwords.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
 * 실행 방법: `node server/app_good_words_server.mjs --host 0.0.0.0 --port 8765`
 */
class SyncWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = (context as AppGoodWordsApplication).container

    // 에뮬레이터에서 호스트 PC를 가리키는 주소.
    private val serverUrl = "http://10.0.2.2:8765"

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
        assumeTrue("서버가 떠 있지 않아 건너뜁니다.", serverIsUp())
        container.settingsStore.updateServerSyncSettings(
            ServerSyncSettings(serverUrl = serverUrl, autoSyncEnabled = true)
        )

        val result = TestListenableWorkerBuilder<SyncWorker>(context).build().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        val status = container.settingsStore.syncStatusFlow.first()
        assertTrue(status.hasSynced)
        assertFalse("실패가 남아 있습니다: ${status.lastError}", status.failed)
    }

    private suspend fun serverIsUp(): Boolean = runCatching {
        container.serverSyncClient.testConnection(ServerSyncSettings(serverUrl = serverUrl))
    }.isSuccess
}
