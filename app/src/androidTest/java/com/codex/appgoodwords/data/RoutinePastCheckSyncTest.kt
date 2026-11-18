package com.codex.appgoodwords.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.codex.appgoodwords.AppGoodWordsApplication
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * 지난날에 적은 체크가 실제 동기화([SyncCoordinator])를 지나 서버에 가고, 되돌리면 서버에서도 지워지는지 봅니다
 * (사용자가 정함, 2026-11-18).
 *
 * 체크는 생긴 시각으로 "바뀐 것만" 고르므로, 지난 시각의 체크는 다음 동기화를 통째로 보내야 갑니다 — 화면이 지난날에 적을 때
 * `MainViewModel.checkRoutine`이 [SettingsStore.requestFullPush]를 부릅니다. 여기서는 그 부탁 뒤의 길을 봅니다.
 *
 * 서버가 떠 있지 않으면 건너뜁니다. testServerUrl에는 운영 데이터가 없는 별도 HTTPS 테스트 서버를 지정합니다.
 */
class RoutinePastCheckSyncTest {
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
        container.database.routineDao().clearAll()
        container.database.routineCheckDao().clearAll()
        container.database.routineMemoDao().clearAll()
        container.database.deletionDao().clearAll()
    }

    private suspend fun serverChecks(): List<RoutineCheckEntity> = client.mergeSnapshot(
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
    ).routineChecks

    @Test
    fun aCheckWrittenForAPastDayReachesTheServerAndItsUndoRemovesIt() = runBlocking {
        val title = "지난날 체크 ${SyncIdentity.newId()}"
        container.repository.saveRoutine(RoutineDraft(title = title))
        val syncId = container.database.routineDao().getAll().single { it.title == title }.syncId
        // 처음 맞추면 보낸 시각이 적힙니다. 이 뒤로는 바뀐 것만 갑니다.
        container.syncCoordinator.merge(SyncBackupKind.BEFORE_MERGE)
        assertTrue(container.settingsStore.getSyncCursor().lastPushAt > 0L)
        // 처음 맞출 때 서버 것으로 갈아엎어 번호가 바뀌었을 수 있습니다. 동기화 식별자로 다시 찾습니다.
        val routine = container.database.routineDao().getAll().single { it.syncId == syncId }

        val twoDaysAgo = LocalDate.now().minusDays(2)
        val check = container.repository.checkRoutineOn(routine.id, twoDaysAgo)!!
        container.settingsStore.requestFullPush()
        container.syncCoordinator.merge(SyncBackupKind.BEFORE_MERGE)

        val sent = serverChecks().single { it.syncId == check.syncId }
        assertEquals(twoDaysAgo, Instant.ofEpochMilli(sent.checkedAt).atZone(ZoneId.systemDefault()).toLocalDate())
        assertEquals(syncId, sent.routineSyncId)

        // 되돌리기는 삭제 표식을 남기고, 표식은 생긴 시각이 지금이라 바뀐 것만 보내도 갑니다.
        assertTrue(container.repository.undoRoutineCheck(check.syncId))
        container.syncCoordinator.merge(SyncBackupKind.BEFORE_MERGE)

        assertTrue("되돌린 체크가 서버에 남았습니다.", serverChecks().none { it.syncId == check.syncId })
    }
}
