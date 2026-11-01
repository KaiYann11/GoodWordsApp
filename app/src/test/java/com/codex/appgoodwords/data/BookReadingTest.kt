package com.codex.appgoodwords.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookReadingTest {
    private val today = LocalDate.of(2026, 10, 30)
    private val book = BookEntity(id = 7, syncId = "book", title = "마지막 장", totalPages = 200, currentPage = 50)

    @Test fun finishingCountsTheRemainingPages() {
        val finished = BookReading.toggleFinished(book, 1_000L)
        assertTrue(BookReading.becameFinished(book, finished))
        assertEquals(200, finished.currentPage)
        assertEquals(1_000L, finished.finishedAt)
        val log = BookReading.record(finished, book.currentPage, today, null, 1_000L)
        assertEquals(150, log.pagesRead)
        assertEquals(today.toString(), log.readDate)
        assertEquals(book.syncId, log.bookSyncId)
    }

    @Test fun anUnknownTotalOrAnAlreadyReachedLastPageStillLeavesADay() {
        for (before in listOf(book.copy(currentPage = 200), book.copy(totalPages = 0, currentPage = 0), book.copy(totalPages = 0))) {
            val finished = BookReading.toggleFinished(before, 1_000L)
            assertTrue(BookReading.becameFinished(before, finished))
            val log = BookReading.record(finished, before.currentPage, today, null, 1_000L)
            assertEquals(0, log.pagesRead)
            val report = BookProgressCalculator.build(finished, listOf(log))
            assertEquals(1, report.readingDays)
            assertEquals(0, report.totalPagesRead)
        }
    }

    @Test fun reopeningDoesNotRequestARecordAndFinishingAgainKeepsTheDaysStart() {
        val finished = BookReading.toggleFinished(book, 1_000L)
        val log = BookReading.record(finished, book.currentPage, today, null, 1_000L).copy(id = 12)
        val reopened = BookReading.toggleFinished(finished, 2_000L)
        assertFalse(BookReading.becameFinished(finished, reopened))
        assertNull(reopened.finishedAt)
        assertEquals(200, reopened.currentPage)
        val again = BookReading.toggleFinished(reopened, 3_000L)
        val sameDay = BookReading.record(again, reopened.currentPage, today, log, 3_000L)
        assertEquals(log.id, sameDay.id)
        assertEquals(log.syncId, sameDay.syncId)
        assertEquals(log.createdAt, sameDay.createdAt)
        assertEquals(50, sameDay.startPage)
        assertEquals(150, sameDay.pagesRead)
        assertEquals(3_000L, sameDay.updatedAt)
    }

    @Test fun finishingAfterProgressKeepsTheFirstPageOfTheDay() {
        val progressed = book.copy(currentPage = 100)
        val log = BookReading.record(progressed, book.currentPage, today, null, 1_000L)
        val finished = BookReading.toggleFinished(progressed, 2_000L)
        val completed = BookReading.record(finished, progressed.currentPage, today, log, 2_000L)
        assertEquals(50, completed.startPage)
        assertEquals(200, completed.endPage)
        assertEquals(150, completed.pagesRead)
    }
}
