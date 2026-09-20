package com.codex.appgoodwords.data

import androidx.test.platform.app.InstrumentationRegistry

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * 앱이 만든 JSON을 서버가 실제로 받아들이는지 확인합니다.
 * 병합 규칙 자체는 SyncMergerTest와 서버 테스트가 덮고, 여기서는 둘 사이의 전송 형식을 봅니다.
 *
 * 서버가 떠 있지 않으면 건너뜁니다.
 * testServerUrl에는 운영 데이터가 없는 별도 HTTPS 테스트 서버를 지정합니다.
 */
class ServerSyncClientIntegrationTest {
    private val client = ServerSyncClient()

    private val settings = ServerSyncSettings(
        serverUrl = InstrumentationRegistry.getArguments().getString("testServerUrl").orEmpty(),
        apiKey = InstrumentationRegistry.getArguments().getString("testServerApiKey").orEmpty()
    )

    @Before
    fun requireServer() {
        assumeTrue("testServerUrl에 별도 HTTPS 테스트 서버를 지정해 주세요.", settings.serverUrl.isNotBlank())
        runBlocking { client.testConnection(settings) }
    }

    @Test
    fun notToDoTextSurvivesEditAndDeletionAcrossDevices() = runBlocking {
        val now = System.currentTimeMillis()
        val rule = ContentItemEntity(
            syncId = "e2e-not-to-do-${SyncIdentity.newId()}", type = ContentType.NOT_TO_DO,
            title = "하지 않을 일 ${SyncIdentity.newId()}", body = "", updatedAt = now - 2_000L
        )
        val sent = client.mergeSnapshot(settings, snapshotWith(items = listOf(rule)))
        assertEquals(ContentType.NOT_TO_DO, sent.items.single { it.syncId == rule.syncId }.type)
        val edited = rule.copy(body = "첫 줄\n둘째 줄", updatedAt = now - 1_000L)
        client.mergeSnapshot(settings, snapshotWith(items = listOf(edited)))
        val stale = client.mergeSnapshot(settings, snapshotWith(items = listOf(rule)))
        assertEquals(edited.body, stale.items.single { it.syncId == rule.syncId }.body)
        client.mergeSnapshot(settings, snapshotWith(deletions = listOf(
            // 보존 기간 안의 표식으로 시험합니다. 1970년 시각을 쓰면 서버가 오래된 표식으로 정리합니다.
            DeletionEntity(syncId = rule.syncId, entityType = SyncEntityType.CONTENT_ITEM, deletedAt = now)
        )))
        val deleted = client.mergeSnapshot(settings, snapshotWith(items = listOf(rule)))
        assertTrue(deleted.items.none { it.syncId == rule.syncId })
    }

    @Test
    fun classifyingInboxTextSurvivesAStaleDeviceSync() = runBlocking {
        val syncId = "e2e-inbox-${SyncIdentity.newId()}"
        val pending = ContentItemEntity(
            syncId = syncId, type = ContentType.INBOX, title = "수집한 글 $syncId",
            body = "원문 첫 줄\n두 번째 줄", sourceUrl = "https://example.com/$syncId",
            updatedAt = 1_000L
        )
        val received = client.mergeSnapshot(settings, snapshotWith(items = listOf(pending)))
        assertEquals(ContentType.INBOX, received.items.single { it.syncId == syncId }.type)

        val memo = pending.copy(type = ContentType.MEMO, updatedAt = 2_000L)
        client.mergeSnapshot(settings, snapshotWith(items = listOf(memo)))
        val merged = client.mergeSnapshot(settings, snapshotWith(items = listOf(pending)))
        val saved = merged.items.single { it.syncId == syncId }
        assertEquals(ContentType.MEMO, saved.type)
        assertEquals(pending.body, saved.body)
        assertEquals(pending.sourceUrl, saved.sourceUrl)
    }

    @Test
    fun mergeSnapshot_sendsAndReceivesSyncFields() = runBlocking {
        // 서버 상태를 공유하므로 테스트마다 다른 식별자를 써서 서로 간섭하지 않게 한다.
        val syncId = "e2e-add-${SyncIdentity.newId()}"
        val title = "기기에만 있는 글귀 $syncId"
        val local = snapshotWith(
            items = listOf(
                ContentItemEntity(
                    syncId = syncId,
                    updatedAt = 2_000L,
                    type = ContentType.QUOTE,
                    title = title,
                    body = "이 기기에서 추가",
                    createdAt = 2_000L
                )
            )
        )

        val merged = client.mergeSnapshot(settings, local)

        val sent = merged.items.firstOrNull { it.syncId == syncId }
        assertTrue("기기 항목이 서버에 반영되어야 합니다.", sent != null)
        assertEquals(title, sent?.title)
        assertEquals(2_000L, sent?.updatedAt)
    }

    @Test
    fun mergeSnapshot_deletionRemovesServerRecord() = runBlocking {
        val syncId = "e2e-del-${SyncIdentity.newId()}"
        client.mergeSnapshot(
            settings,
            snapshotWith(
                items = listOf(
                    ContentItemEntity(
                        syncId = syncId,
                        updatedAt = 1_000L,
                        type = ContentType.QUOTE,
                        title = "지울 항목 $syncId",
                        body = "본문",
                        createdAt = 1_000L
                    )
                )
            )
        )

        val merged = client.mergeSnapshot(
            settings,
            snapshotWith(
                deletions = listOf(
                    DeletionEntity(
                        syncId = syncId,
                        entityType = SyncEntityType.CONTENT_ITEM,
                        deletedAt = 9_000L
                    )
                )
            )
        )

        assertTrue(
            "삭제 표식을 보낸 항목이 남아 있습니다.",
            merged.items.none { it.syncId == syncId }
        )
    }

    @Test
    fun mergeSnapshot_carriesDiariesAndTodos() = runBlocking {
        val diarySyncId = "e2e-diary-${SyncIdentity.newId()}"
        val todoSyncId = "e2e-todo-${SyncIdentity.newId()}"
        // 내용까지 매번 달라야 합니다. 같으면 지난 실행이 남긴 기록과 "같은 내용 합치기"에 걸려
        // 둘 중 syncId가 큰 쪽만 남고, 이 테스트가 만든 쪽이 사라질 수 있습니다.
        val diaryTitle = "기기에서 쓴 일기 $diarySyncId"
        val todoTitle = "기기에서 만든 할 일 $todoSyncId"

        val merged = client.mergeSnapshot(
            settings,
            snapshotWith(
                diaries = listOf(
                    DiaryEntity(
                        syncId = diarySyncId,
                        updatedAt = 2_000L,
                        entryDate = "2026-08-12",
                        title = diaryTitle,
                        body = "본문",
                        weather = DiaryWeather.RAIN.name,
                        mood = DiaryMood.GOOD.name,
                        imageUris = listOf("content://photo/1"),
                        audioUris = listOf("content://audio/1"),
                        createdAt = 2_000L
                    )
                ),
                todos = listOf(
                    TodoEntity(
                        syncId = todoSyncId,
                        updatedAt = 2_000L,
                        title = todoTitle,
                        dueDate = "2026-08-12",
                        remindAt = 3_000L,
                        createdAt = 2_000L
                    )
                )
            )
        )

        val diary = merged.diaries.firstOrNull { it.syncId == diarySyncId }
        assertEquals("2026-08-12", diary?.entryDate)
        assertEquals("RAIN", diary?.weather)
        assertEquals("GOOD", diary?.mood)
        assertEquals(listOf("content://photo/1"), diary?.imageUris)
        assertEquals("첨부 종류가 섞이면 안 됩니다.", listOf("content://audio/1"), diary?.audioUris)

        val todo = merged.todos.firstOrNull { it.syncId == todoSyncId }
        assertEquals(todoTitle, todo?.title)
        assertEquals(3_000L, todo?.remindAt)
        assertEquals("누르지 않은 완료 표시가 생겼습니다.", null, todo?.doneAt)
    }

    private fun snapshotWith(
        items: List<ContentItemEntity> = emptyList(),
        deletions: List<DeletionEntity> = emptyList(),
        diaries: List<DiaryEntity> = emptyList(),
        todos: List<TodoEntity> = emptyList()
    ) = AppDataSnapshot(
        items = items,
        events = emptyList(),
        routines = emptyList(),
        routineChecks = emptyList(),
        routineMemos = emptyList(),
        settings = ReminderSettings(),
        settingsUpdatedAt = 0L,
        deletions = deletions,
        diaries = diaries,
        todos = todos
    )

    @Test
    fun testConnection_reportsMatchingSchemaVersion() = runBlocking {
        val info = client.testConnection(settings)

        assertEquals(AppDataJson.schemaVersion, info.serverSchemaVersion)
        assertTrue(info.schemaMatches)
    }
}
