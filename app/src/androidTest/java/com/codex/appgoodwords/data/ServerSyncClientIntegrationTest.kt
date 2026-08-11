package com.codex.appgoodwords.data

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
 * 실행 방법: `node server/app_good_words_server.mjs --host 0.0.0.0 --port 8765`
 */
class ServerSyncClientIntegrationTest {
    private val client = ServerSyncClient()

    // 에뮬레이터에서 호스트 PC를 가리키는 주소.
    private val settings = ServerSyncSettings(serverUrl = "http://10.0.2.2:8765", apiKey = "")

    @Before
    fun requireServer() {
        val reachable = runCatching { runBlocking { client.testConnection(settings) } }.isSuccess
        assumeTrue("서버가 떠 있지 않아 건너뜁니다.", reachable)
    }

    @Test
    fun mergeSnapshot_sendsAndReceivesSyncFields() = runBlocking {
        // 서버 상태를 공유하므로 테스트마다 다른 식별자를 써서 서로 간섭하지 않게 한다.
        val syncId = "e2e-add-${SyncIdentity.newId()}"
        val local = snapshotWith(
            items = listOf(
                ContentItemEntity(
                    syncId = syncId,
                    updatedAt = 2_000L,
                    type = ContentType.QUOTE,
                    title = "기기에만 있는 글귀",
                    body = "이 기기에서 추가",
                    createdAt = 2_000L
                )
            )
        )

        val merged = client.mergeSnapshot(settings, local)

        val sent = merged.items.firstOrNull { it.syncId == syncId }
        assertTrue("기기 항목이 서버에 반영되어야 합니다.", sent != null)
        assertEquals("기기에만 있는 글귀", sent?.title)
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
                        title = "지울 항목",
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

        val merged = client.mergeSnapshot(
            settings,
            snapshotWith(
                diaries = listOf(
                    DiaryEntity(
                        syncId = diarySyncId,
                        updatedAt = 2_000L,
                        entryDate = "2026-08-12",
                        title = "기기에서 쓴 일기",
                        body = "본문",
                        imageUris = listOf("content://photo/1"),
                        audioUris = listOf("content://audio/1"),
                        createdAt = 2_000L
                    )
                ),
                todos = listOf(
                    TodoEntity(
                        syncId = todoSyncId,
                        updatedAt = 2_000L,
                        title = "기기에서 만든 할 일",
                        dueDate = "2026-08-12",
                        remindAt = 3_000L,
                        createdAt = 2_000L
                    )
                )
            )
        )

        val diary = merged.diaries.firstOrNull { it.syncId == diarySyncId }
        assertEquals("2026-08-12", diary?.entryDate)
        assertEquals(listOf("content://photo/1"), diary?.imageUris)
        assertEquals("첨부 종류가 섞이면 안 됩니다.", listOf("content://audio/1"), diary?.audioUris)

        val todo = merged.todos.firstOrNull { it.syncId == todoSyncId }
        assertEquals("기기에서 만든 할 일", todo?.title)
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
