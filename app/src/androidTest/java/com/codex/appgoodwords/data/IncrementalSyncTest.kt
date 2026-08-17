package com.codex.appgoodwords.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.codex.appgoodwords.AppGoodWordsApplication
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * 두 번째 동기화부터는 바뀐 것만 오가는지 봅니다.
 *
 * 이력이 수천 건 쌓이면 전체 스냅샷이 1MB에 가까워지는데, 그중 새로 생긴 것은 몇 건뿐입니다.
 * 여기서 확인하려는 것은 "덜 보내면서도 안 잃는가"입니다.
 *
 * 서버가 떠 있지 않으면 건너뜁니다.
 * 실행 방법: `node server/app_good_words_server.mjs --host 0.0.0.0 --port 8765`
 */
class IncrementalSyncTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = (context as AppGoodWordsApplication).container
    private val client = ServerSyncClient()
    private val settings = ServerSyncSettings(serverUrl = "http://10.0.2.2:8765", apiKey = "")

    @Before
    fun requireServer() {
        val reachable = runCatching { runBlocking { client.testConnection(settings) } }.isSuccess
        assumeTrue("서버가 떠 있지 않아 건너뜁니다.", reachable)
    }

    @After
    fun tearDown() = runBlocking {
        container.database.bookDao().clearAll()
        container.settingsStore.clearSyncCursor()
    }

    @Test
    fun theFirstRoundIsFullAndTheSecondIsPartial() = runBlocking {
        val first = client.mergeSnapshot(settings, snapshotWith(book("첫 책-${SyncIdentity.newId()}")), since = 0L)

        assertTrue("처음에는 전체를 받아야 합니다.", !first.partial)
        assertTrue("리비전 번호가 없습니다.", first.serverRev > 0L)

        val second = client.mergeSnapshot(settings, snapshotWith(), since = first.serverRev)

        assertTrue("두 번째부터는 바뀐 것만 받아야 합니다.", second.partial)
        // 아무것도 안 바뀌었으니 실어 올 것이 없습니다.
        assertEquals(0, second.books.size)
        assertEquals(0, second.items.size)
    }

    @Test
    fun onlyTheNewRecordComesBack() = runBlocking {
        val first = client.mergeSnapshot(settings, snapshotWith(book("먼저 담은 책-${SyncIdentity.newId()}")), since = 0L)

        val fresh = book("나중 담은 책-${SyncIdentity.newId()}")
        val second = client.mergeSnapshot(settings, snapshotWith(fresh), since = first.serverRev)

        assertEquals(listOf(fresh.syncId), second.books.map { it.syncId })
    }

    @Test
    fun theServerRevisionMovesForwardOnlyWhenSomethingChanges() = runBlocking {
        val first = client.mergeSnapshot(settings, snapshotWith(book("책-${SyncIdentity.newId()}")), since = 0L)

        val idle = client.mergeSnapshot(settings, snapshotWith(), since = first.serverRev)

        assertEquals("바뀐 게 없는데 번호가 올랐습니다.", first.serverRev, idle.serverRev)
    }

    @Test
    fun applyingADeltaKeepsWhatWasNotSent() = runBlocking {
        // 이 기기에만 있는 책. 부분 응답에는 담기지 않습니다.
        val untouched = container.repository.saveBook(BookDraft(title = "건드리지 않은 책-${SyncIdentity.newId()}"))

        val arriving = book("다른 기기에서 온 책-${SyncIdentity.newId()}")
        val delta = AppDataSnapshot(
            items = emptyList(),
            events = emptyList(),
            routines = emptyList(),
            routineChecks = emptyList(),
            routineMemos = emptyList(),
            settings = ReminderSettings(),
            books = listOf(arriving),
            partial = true
        )

        container.appDataImporter.applyDelta(delta)

        val titles = container.database.bookDao().getAll().map { it.title }
        // 통째로 갈아엎으면 여기서 사라집니다. 부분 응답에 없다고 지워진 것이 아닙니다.
        assertTrue("안 보낸 책이 사라졌습니다: $titles", titles.contains(untouched.title))
        assertTrue("받은 책이 안 들어왔습니다: $titles", titles.contains(arriving.title))
    }

    @Test
    fun applyingADeltaKeepsThisDevicesNumbering() = runBlocking {
        val mine = container.repository.saveBook(BookDraft(title = "이 기기 책-${SyncIdentity.newId()}"))

        // 서버가 붙인 번호가 이 기기의 다른 책과 겹치는 상황.
        val arriving = book("서버에서 온 책-${SyncIdentity.newId()}").copy(id = mine.id)
        container.appDataImporter.applyDelta(
            AppDataSnapshot(
                items = emptyList(),
                events = emptyList(),
                routines = emptyList(),
                routineChecks = emptyList(),
                routineMemos = emptyList(),
                settings = ReminderSettings(),
                books = listOf(arriving),
                partial = true
            )
        )

        val stored = container.database.bookDao().getAll()
        // 서버 번호를 그대로 쓰면 이 기기 책을 덮어씁니다.
        assertNotNull("이 기기 책이 덮어써졌습니다.", stored.firstOrNull { it.syncId == mine.syncId })
        assertNotNull(stored.firstOrNull { it.syncId == arriving.syncId })
    }

    @Test
    fun aDeletionInTheDeltaRemovesTheLocalRecord() = runBlocking {
        val doomed = container.repository.saveBook(BookDraft(title = "지워질 책-${SyncIdentity.newId()}"))

        container.appDataImporter.applyDelta(
            AppDataSnapshot(
                items = emptyList(),
                events = emptyList(),
                routines = emptyList(),
                routineChecks = emptyList(),
                routineMemos = emptyList(),
                settings = ReminderSettings(),
                deletions = listOf(
                    DeletionEntity(
                        syncId = doomed.syncId,
                        entityType = SyncEntityType.BOOK,
                        deletedAt = System.currentTimeMillis()
                    )
                ),
                partial = true
            )
        )

        assertEquals(null, container.database.bookDao().getById(doomed.id))
    }

    private fun book(title: String) = BookEntity(
        syncId = "e2e-book-${SyncIdentity.newId()}",
        updatedAt = System.currentTimeMillis(),
        title = title
    )

    private fun snapshotWith(vararg books: BookEntity) = AppDataSnapshot(
        items = emptyList(),
        events = emptyList(),
        routines = emptyList(),
        routineChecks = emptyList(),
        routineMemos = emptyList(),
        settings = ReminderSettings(),
        books = books.toList()
    )
}
