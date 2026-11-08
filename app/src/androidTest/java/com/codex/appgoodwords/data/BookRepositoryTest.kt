package com.codex.appgoodwords.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.codex.appgoodwords.AppGoodWordsApplication
import java.time.LocalDate
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
    fun setUp() = runBlocking {
        container.database.bookDao().clearAll()
        container.database.bookReadingLogDao().clearAll()
    }

    @After
    fun tearDown() = runBlocking {
        container.database.bookDao().clearAll()
        container.database.bookReadingLogDao().clearAll()
    }

    @Test
    fun finishingRecordsADayWithRemainingPagesNoPagesOrAnUnknownTotal() = runBlocking {
        val today = LocalDate.of(2026, 10, 30)
        for ((current, total) in listOf(50 to 200, 200 to 200, 0 to 0, 50 to 0)) {
            val book = repository.saveBook(BookDraft(title = "완료 $current $total", currentPageText = "$current", totalPagesText = "$total"))
            repository.toggleBookFinished(book.id, today)
            val log = container.database.bookReadingLogDao().getAll().single { it.bookSyncId == book.syncId }
            assertEquals(today.toString(), log.readDate)
            assertEquals(current, log.startPage)
            assertEquals(if (total > 0) total - current else 0, log.pagesRead)
        }
    }

    @Test
    fun reopeningLeavesTheRecordAloneAndFinishingTwiceKeepsOneDay() = runBlocking {
        val today = LocalDate.of(2026, 10, 30)
        val book = repository.saveBook(BookDraft(title = "완료와 되돌리기", totalPagesText = "200", currentPageText = "50"))
        repository.updateBookProgress(book.id, 100, today)
        repository.toggleBookFinished(book.id, today)
        val first = container.database.bookReadingLogDao().getAll().single()
        assertEquals(150, first.pagesRead)
        repository.toggleBookFinished(book.id, today)
        assertEquals(listOf(first), container.database.bookReadingLogDao().getAll())
        repository.toggleBookFinished(book.id, today)
        val repeated = container.database.bookReadingLogDao().getAll().single()
        assertEquals(first.syncId, repeated.syncId)
        assertEquals(50, repeated.startPage)
        assertEquals(150, repeated.pagesRead)
        repository.toggleBookFinished(book.id, today.plusDays(1))
        assertEquals(listOf(repeated), container.database.bookReadingLogDao().getAll())
    }

    @Test
    fun extractingWithoutActualProgressDoesNotCreateAReadingDay() = runBlocking {
        val book = repository.saveBook(BookDraft(title = "이미 마지막 쪽", totalPagesText = "100", currentPageText = "100"))
        for (page in listOf(0, 30, 100, 999)) {
            repository.extractQuoteFromBook(book.id, "다시 뽑은 문장 $page", page)
        }
        assertTrue(container.database.bookReadingLogDao().getAll().isEmpty())
        assertEquals(100, repository.getBookById(book.id)?.currentPage)
    }

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
    fun aProgressNoteIsAddedToTheEndOfTheMemo() = runBlocking {
        val book = repository.saveBook(BookDraft(title = "메모 쌓을 책", totalPagesText = "400", note = "빌린 책"))

        repository.updateBookProgress(book.id, 96, LocalDate.of(2026, 11, 5), note = "초반은 느리다")
        // 쪽이 끝을 넘으면 깎인 쪽이 적힙니다. 메모의 쪽이 카드의 진도와 달라 보이면 안 됩니다.
        repository.updateBookProgress(book.id, 999, LocalDate.of(2026, 11, 8), note = "다 읽었다")
        // 남길 말이 없으면 메모는 그대로입니다.
        val last = repository.updateBookProgress(book.id, 400, LocalDate.of(2026, 11, 9))

        assertEquals(
            "빌린 책\n11월 5일 · 96쪽 — 초반은 느리다\n11월 8일 · 400쪽 — 다 읽었다",
            last?.note
        )
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
