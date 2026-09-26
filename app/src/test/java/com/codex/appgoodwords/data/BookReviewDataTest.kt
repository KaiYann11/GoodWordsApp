package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 독후감이 오가는 길.
 *
 * 긴 글이라 한 번 잃으면 되돌릴 수 없습니다. 그래서 백업·병합 두 길에서 글자 그대로 남는지,
 * 특히 **두 기기에서 같은 책을 고쳤을 때 나중 것이 남는지**를 봅니다.
 */
class BookReviewDataTest {
    private val book = BookEntity(
        id = 1,
        syncId = "book-1",
        title = "아주 작은 습관의 힘",
        author = "제임스 클리어",
        currentPage = 120,
        note = "2장까지",
        review = "첫 문단입니다.\n\n빈 줄을 사이에 둔 둘째 문단입니다.",
        updatedAt = 100
    )

    @Test fun theReviewSurvivesABackupWordForWord() {
        val restored = AppDataJson.fromJsonText(AppDataJson.toJson(snapshot(listOf(book))).toString())

        // 가운데 빈 줄은 사용자가 문단을 나눈 자리입니다. 다듬어 버리면 글의 모양이 달라집니다.
        assertEquals(book.review, restored.books.single().review)
        assertEquals(listOf(book), restored.books)
    }

    @Test fun anOlderBackupWithoutAReviewDoesNotBlankTheField() {
        // 형식 21 이하에서 만든 파일에는 이 칸이 없습니다. 없는 것을 빈 글로 읽어도
        // 병합에서 밀리지 않아야, 옛 파일을 넣었다고 써 둔 글이 사라지지 않습니다.
        val json = AppDataJson.toJson(snapshot(listOf(book))).toString().replace("\"review\"", "\"reviewGone\"")
        val restored = AppDataJson.fromJsonText(json)
        assertEquals("", restored.books.single().review)

        val merged = SyncMerger.merge(snapshot(listOf(book)), restored)
        assertEquals(book.review, merged.books.single().review)
    }

    @Test fun theLatestWriterWins() {
        val later = book.copy(review = book.review + "\n다른 기기에서 이어 썼습니다.", updatedAt = 200)
        val merged = SyncMerger.merge(snapshot(listOf(later)), snapshot(listOf(book)))

        assertEquals(later.review, merged.books.single().review)
    }

    @Test fun twoBooksWithTheSameTitleStillCollapseIntoOne() {
        // 합치는 기준은 제목·저자입니다. 독후감을 기준에 넣으면 같은 책이 두 벌 남습니다.
        val other = book.copy(id = 2, syncId = "book-2", review = "다른 기기에서 쓴 글", updatedAt = 200)
        val deduped = SyncDeduplicator.deduplicate(snapshot(listOf(book, other)))

        assertEquals(1, deduped.books.size)
        assertEquals("다른 기기에서 쓴 글", deduped.books.single().review)
    }

    @Test fun hasReviewTellsAnEmptyOneFromAWrittenOne() {
        assertTrue(book.hasReview)
        assertFalse(book.copy(review = "").hasReview)
        assertFalse(book.copy(review = "   \n  ").hasReview)
    }

    private fun snapshot(books: List<BookEntity>) = AppDataSnapshot(
        items = emptyList(), events = emptyList(), routines = emptyList(), routineChecks = emptyList(),
        routineMemos = emptyList(), settings = ReminderSettings(), books = books
    )
}
