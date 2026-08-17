package com.codex.appgoodwords.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.codex.appgoodwords.AppGoodWordsApplication
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 책의 진도와 "글귀 뽑기"가 실제 DB에서 어떻게 움직이는지 봅니다.
 *
 * 진도가 뒤로 가거나 다 읽은 책이 다시 읽는 중으로 돌아가면 목록이 뒤집힙니다.
 */
class BookRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = (context as AppGoodWordsApplication).container
    private val repository = container.repository

    @Before
    fun setUp() = runBlocking { container.database.bookDao().clearAll() }

    @After
    fun tearDown() = runBlocking { container.database.bookDao().clearAll() }

    @Test
    fun aSavedBookStartsAsReading() = runBlocking {
        val book = repository.saveBook(BookDraft(title = "새로 담은 책", author = "저자"))

        assertEquals(BookStatus.READING, book.statusOption)
        assertNotNull("읽기 시작한 시각이 없습니다.", book.startedAt)
        assertNull(book.finishedAt)
    }

    @Test
    fun aBookWithoutATitleIsRefused() = runBlocking {
        val result = runCatching { repository.saveBook(BookDraft(title = "   ")) }

        assertTrue(result.isFailure)
    }

    @Test
    fun reachingTheLastPageFinishesTheBook() = runBlocking {
        val book = repository.saveBook(BookDraft(title = "끝까지 읽을 책", totalPagesText = "200"))

        val updated = repository.updateBookProgress(book.id, 200)

        // 마지막 쪽에 닿았는데 읽는 중에 남아 있으면 한 번 더 눌러야 합니다.
        assertEquals(BookStatus.FINISHED, updated?.statusOption)
        assertNotNull(updated?.finishedAt)
    }

    @Test
    fun progressNeverPassesTheLastPage() = runBlocking {
        val book = repository.saveBook(BookDraft(title = "쪽수 있는 책", totalPagesText = "100"))

        val updated = repository.updateBookProgress(book.id, 999)

        assertEquals(100, updated?.currentPage)
        assertEquals(1f, updated?.progress)
    }

    @Test
    fun reopeningAFinishedBookClearsTheFinishMark() = runBlocking {
        val book = repository.saveBook(BookDraft(title = "다시 읽을 책", totalPagesText = "100"))
        repository.toggleBookFinished(book.id)

        val reopened = repository.toggleBookFinished(book.id)

        assertEquals(BookStatus.READING, reopened?.statusOption)
        // 완독 시각이 남아 있으면 목록에서 다 읽은 책으로 셉니다.
        assertNull(reopened?.finishedAt)
    }

    @Test
    fun extractingAQuoteFillsTheSourceFromTheBook() = runBlocking {
        val book = repository.saveBook(BookDraft(title = "출처 있는 책", author = "지은이", totalPagesText = "300"))

        val quote = repository.extractQuoteFromBook(book.id, "행동이 먼저다.", page = 42)

        assertEquals(ContentType.QUOTE, quote.type)
        // 사용자가 저자와 제목을 다시 적지 않아도 되어야 합니다.
        assertEquals("지은이", quote.author)
        assertEquals("출처 있는 책", quote.title)
        assertEquals(book.syncId, quote.bookSyncId)
        assertEquals(42, quote.bookPage)
        assertEquals(AppRepository.BOOK_CATEGORY, quote.category)
    }

    @Test
    fun extractingAQuoteMovesTheProgressForward() = runBlocking {
        val book = repository.saveBook(BookDraft(title = "진도 따라오는 책", totalPagesText = "300"))

        repository.extractQuoteFromBook(book.id, "여기까지 읽었다.", page = 88)

        // 뽑았다는 것은 거기까지 읽었다는 뜻입니다.
        assertEquals(88, repository.getBookById(book.id)?.currentPage)
    }

    @Test
    fun extractingAnEarlierQuoteDoesNotRewindTheProgress() = runBlocking {
        val book = repository.saveBook(
            BookDraft(title = "되돌아가지 않는 책", totalPagesText = "300", currentPageText = "200")
        )

        repository.extractQuoteFromBook(book.id, "앞쪽 문장", page = 30)

        assertEquals(200, repository.getBookById(book.id)?.currentPage)
    }

    @Test
    fun anEmptyQuoteIsRefused() = runBlocking {
        val book = repository.saveBook(BookDraft(title = "빈 글귀 책"))

        val result = runCatching { repository.extractQuoteFromBook(book.id, "   ", page = 1) }

        assertTrue(result.isFailure)
    }

    @Test
    fun deletingABookKeepsTheQuotesTakenFromIt() = runBlocking {
        val book = repository.saveBook(BookDraft(title = "지울 책"))
        val quote = repository.extractQuoteFromBook(book.id, "남아야 하는 문장", page = 10)

        repository.deleteBook(book.id)

        assertNull(repository.getBookById(book.id))
        // 책을 정리했다고 밑줄 그은 문장까지 사라지면 안 됩니다.
        assertNotNull(container.database.contentItemDao().getById(quote.id))
    }

    @Test
    fun shrinkingTheTotalPagesPullsTheProgressBack() = runBlocking {
        val book = repository.saveBook(
            BookDraft(title = "쪽수를 고친 책", totalPagesText = "500", currentPageText = "400")
        )

        val fixed = repository.saveBook(
            BookDraft.from(book).copy(totalPagesText = "300")
        )

        // 전체보다 많이 읽은 책은 있을 수 없습니다. 화면이 100%를 넘겨 그립니다.
        assertEquals(300, fixed.currentPage)
    }
}
