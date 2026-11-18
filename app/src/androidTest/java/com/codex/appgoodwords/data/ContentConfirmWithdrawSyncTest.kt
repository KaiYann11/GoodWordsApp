package com.codex.appgoodwords.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.codex.appgoodwords.AppGoodWordsApplication
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * 서버에 이미 올라간 읽음을 거두면 실제 동기화([SyncCoordinator])를 지나 서버에서도 지워지는지 봅니다.
 *
 * 읽음은 덧붙기만 하는 기록이라 삭제 표식이 있어야 서버가 지웁니다. 1.24.1까지 읽음을 다시 눌러 거둘 때는 표식이 없어
 * 서버와 웹에 남았습니다. 홈 글귀를 민 것의 되돌리기도 같은 길(`AppRepository.withdrawTodayConfirmed`)로 거둡니다.
 *
 * 서버가 떠 있지 않으면 건너뜁니다. testServerUrl에는 운영 데이터가 없는 별도 HTTPS 테스트 서버를 지정합니다.
 */
class ContentConfirmWithdrawSyncTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = (context as AppGoodWordsApplication).container
    private val client = ServerSyncClient()
    private val settings = ServerSyncSettings(
        serverUrl = InstrumentationRegistry.getArguments().getString("testServerUrl").orEmpty(),
        apiKey = InstrumentationRegistry.getArguments().getString("testServerApiKey").orEmpty()
    )
    private var previous = ServerSyncSettings()

    @Before
    fun requireServer() {
        assumeTrue("testServerUrl에 별도 HTTPS 테스트 서버를 지정해 주세요.", settings.serverUrl.isNotBlank())
        runBlocking {
            client.testConnection(settings)
            previous = container.settingsStore.getServerSyncSettings()
            container.settingsStore.updateServerSyncSettings(settings)
            clear()
        }
    }

    @After
    fun tearDown() = runBlocking {
        if (settings.serverUrl.isBlank()) return@runBlocking
        clear()
        container.settingsStore.updateServerSyncSettings(previous)
        container.settingsStore.clearSyncCursor()
    }

    private suspend fun clear() {
        container.database.contentItemDao().clearAll()
        container.database.exposureEventDao().clearAll()
        container.database.deletionDao().clearAll()
    }

    private suspend fun serverEvents(): List<ExposureEventEntity> = client.mergeSnapshot(
        settings,
        AppDataSnapshot(
            items = emptyList(),
            events = emptyList(),
            routines = emptyList(),
            routineChecks = emptyList(),
            routineMemos = emptyList(),
            settings = ReminderSettings(),
            settingsUpdatedAt = 0L
        ),
        since = 0L
    ).events

    @Test
    fun aReadTakenBackAfterItWasSentIsRemovedFromTheServer() = runBlocking {
        val body = "거둔 읽음 ${SyncIdentity.newId()}"
        container.database.contentItemDao().insert(ContentItemEntity(type = ContentType.QUOTE, title = "", body = body))
        // 처음 맞추면 보낸 시각이 적힙니다. 이 뒤로는 바뀐 것만 갑니다.
        container.syncCoordinator.merge(SyncBackupKind.BEFORE_MERGE)
        // 처음 맞출 때 서버 것으로 갈아엎어 번호가 바뀌었을 수 있습니다. 본문으로 다시 찾습니다.
        val item = container.database.contentItemDao().getAll().single { it.body == body }

        assertTrue(container.repository.toggleContentConfirmed(item.id, ExposureTrigger.DETAIL_CHECK))
        container.syncCoordinator.merge(SyncBackupKind.BEFORE_MERGE)
        val read = container.database.exposureEventDao().getAll()
            .single { it.contentItemSyncId == item.syncId && it.eventType == ExposureEventType.CONFIRMED }
        assertTrue("읽음이 서버에 가지 않았습니다.", serverEvents().any { it.syncId == read.syncId })

        // 읽음을 다시 눌러 거둡니다. 표식은 생긴 시각이 지금이라 바뀐 것만 보내도 갑니다.
        assertFalse(container.repository.toggleContentConfirmed(item.id, ExposureTrigger.DETAIL_CHECK))
        container.syncCoordinator.merge(SyncBackupKind.BEFORE_MERGE)

        assertTrue("거둔 읽음이 서버에 남았습니다.", serverEvents().none { it.syncId == read.syncId })
        // 한 번 더 맞춰도 서버의 옛 사본이 되살아나지 않습니다.
        container.syncCoordinator.merge(SyncBackupKind.BEFORE_MERGE)
        assertTrue(container.database.exposureEventDao().getAll().none { it.syncId == read.syncId })
    }
}
