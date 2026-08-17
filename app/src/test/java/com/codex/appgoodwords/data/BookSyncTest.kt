package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 책이 기기 사이를 오갈 때 무엇을 잃는지 봅니다.
 *
 * 진도는 기기마다 달라지는 값이라, 합치는 규칙이 어긋나면 같은 책이 두 벌이 되거나
 * 늦게 읽은 쪽이 앞선 진도를 되돌립니다.
 */
class BookSyncTest {
    @Test
    fun jsonKeepsEverythingTheScreenShows() {
        val snapshot = snapshotOf(
            books = listOf(
                BookEntity(
                    id = 3,
                    syncId = "book-1",
                    updatedAt = 5_000L,
                    title = "아주 작은 습관의 힘",
                    author = "제임스 클리어",
                    totalPages = 320,
                    currentPage = 120,
                    status = BookStatus.READING.name,
                    note = "3장부터 다시",
                    startedAt = 4_000L,
                    finishedAt = null,
                    createdAt = 4_000L
                )
            )
        )

        val restored = AppDataJson.fromJsonText(AppDataJson.toJson(snapshot).toString())

        assertEquals(snapshot.books, restored.books)
    }

    @Test
    fun anUnfinishedBookStaysUnfinished() {
        // 0으로 돌아오면 1970년에 다 읽은 책이 된다.
        val snapshot = snapshotOf(
            books = listOf(BookEntity(syncId = "book-1", title = "읽는 중", finishedAt = null))
        )

        val restored = AppDataJson.fromJsonText(AppDataJson.toJson(snapshot).toString())

        assertNull(restored.books.single().finishedAt)
    }

    @Test
    fun aBookWithoutATitleIsDropped() {
        val json = """{"books":[{"syncId":"book-1","updatedAt":1000,"title":"  "}]}"""

        // 제목이 없으면 목록에서 무엇인지 알 수 없어 놓을 자리가 없다.
        assertTrue(AppDataJson.fromJsonText(json).books.isEmpty())
    }

    @Test
    fun aQuoteRemembersWhichBookItCameFrom() {
        val snapshot = snapshotOf(
            items = listOf(
                ContentItemEntity(
                    syncId = "quote-1",
                    type = ContentType.QUOTE,
                    title = "출처 있는 책",
                    body = "행동이 먼저다.",
                    bookSyncId = "book-1",
                    bookPage = 42
                )
            )
        )

        val restored = AppDataJson.fromJsonText(AppDataJson.toJson(snapshot).toString())

        assertEquals("book-1", restored.items.single().bookSyncId)
        assertEquals(42, restored.items.single().bookPage)
    }

    @Test
    fun theLatestProgressWins() {
        val local = snapshotOf(
            books = listOf(BookEntity(syncId = "book-1", updatedAt = 1_000L, title = "같은 책", currentPage = 30))
        )
        val remote = snapshotOf(
            books = listOf(BookEntity(syncId = "book-1", updatedAt = 2_000L, title = "같은 책", currentPage = 80))
        )

        val merged = SyncMerger.merge(local, remote)

        assertEquals(1, merged.books.size)
        assertEquals(80, merged.books.single().currentPage)
    }

    @Test
    fun theSameBookFromTwoDevicesBecomesOne() {
        // 새 기기에서 같은 책을 다시 담으면 syncId가 달라 두 벌이 된다.
        val merged = snapshotOf(
            books = listOf(
                BookEntity(syncId = "b1", updatedAt = 1_000L, title = "같은 책", author = "같은 저자", currentPage = 30),
                BookEntity(syncId = "b2", updatedAt = 2_000L, title = "같은 책", author = "같은 저자", currentPage = 80)
            )
        )

        val result = SyncDeduplicator.deduplicate(merged)

        assertEquals(1, result.books.size)
        assertEquals(80, result.books.single().currentPage)
    }

    @Test
    fun differentBooksByTheSameAuthorAreKept() {
        val merged = snapshotOf(
            books = listOf(
                BookEntity(syncId = "b1", updatedAt = 1_000L, title = "첫 책", author = "같은 저자"),
                BookEntity(syncId = "b2", updatedAt = 2_000L, title = "다른 책", author = "같은 저자")
            )
        )

        val result = SyncDeduplicator.deduplicate(merged)

        assertEquals(2, result.books.size)
    }

    @Test
    fun aQuoteFollowsTheBookThatSurvived() {
        val merged = snapshotOf(
            books = listOf(
                BookEntity(syncId = "b1", updatedAt = 1_000L, title = "합쳐질 책", author = "저자"),
                BookEntity(syncId = "b2", updatedAt = 2_000L, title = "합쳐질 책", author = "저자")
            ),
            items = listOf(
                ContentItemEntity(
                    syncId = "q1",
                    type = ContentType.QUOTE,
                    title = "합쳐질 책",
                    body = "출처를 잃으면 안 되는 문장",
                    bookSyncId = "b1"
                )
            )
        )

        val result = SyncDeduplicator.deduplicate(merged)

        assertEquals("b2", result.books.single().syncId)
        assertEquals("글귀가 출처를 잃었습니다.", "b2", result.items.single().bookSyncId)
    }

    @Test
    fun aQuoteWithoutABookIsLeftAlone() {
        val merged = snapshotOf(
            items = listOf(
                ContentItemEntity(syncId = "q1", type = ContentType.QUOTE, title = "그냥 글귀", body = "본문")
            )
        )

        val result = SyncDeduplicator.deduplicate(merged)

        assertEquals("", result.items.single().bookSyncId)
    }

    @Test
    fun reindexKeepsBothDevicesBooks() {
        // 숫자 id는 기기마다 따로 증가해서 A기기 1번과 B기기 1번이 함께 들어온다.
        val merged = snapshotOf(
            books = listOf(
                BookEntity(id = 1, syncId = "book-a", title = "A기기 책"),
                BookEntity(id = 1, syncId = "book-b", title = "B기기 책")
            )
        )

        val result = SnapshotReindexer.reindex(merged)

        assertEquals(listOf(1L, 2L), result.books.map { it.id })
        assertEquals(listOf("book-a", "book-b"), result.books.map { it.syncId })
    }

    @Test
    fun mergeHonoursDeletionMarks() {
        val local = snapshotOf(
            books = listOf(BookEntity(syncId = "book-1", updatedAt = 1_000L, title = "지울 책"))
        )
        val remote = snapshotOf(
            deletions = listOf(
                DeletionEntity(syncId = "book-1", entityType = SyncEntityType.BOOK, deletedAt = 2_000L)
            )
        )

        val merged = SyncMerger.merge(local, remote)

        assertTrue("지운 책이 되살아났습니다.", merged.books.isEmpty())
    }

    @Test
    fun progressIsUnknownWithoutATotal() {
        // 전체 쪽수를 모를 때 0을 돌려주면 화면이 "0% 읽음"이라고 단정합니다.
        assertNull(BookEntity(title = "쪽수 모르는 책", currentPage = 50).progress)
        assertEquals(0.5f, BookEntity(title = "책", totalPages = 100, currentPage = 50).progress!!, 0.001f)
    }

    private fun snapshotOf(
        items: List<ContentItemEntity> = emptyList(),
        books: List<BookEntity> = emptyList(),
        deletions: List<DeletionEntity> = emptyList()
    ) = AppDataSnapshot(
        items = items,
        events = emptyList(),
        routines = emptyList(),
        routineChecks = emptyList(),
        routineMemos = emptyList(),
        settings = ReminderSettings(),
        deletions = deletions,
        books = books
    )
}
