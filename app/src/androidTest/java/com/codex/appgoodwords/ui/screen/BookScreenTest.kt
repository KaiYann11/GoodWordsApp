package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.codex.appgoodwords.data.BookDraft
import com.codex.appgoodwords.data.BookEntity
import com.codex.appgoodwords.data.BookStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 읽는 책과 읽은 책이 어떻게 갈려 보이는지, 글귀 뽑기가 무엇을 넘기는지 확인합니다.
 */
class BookScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun readingAndFinishedBooksAreSeparated() {
        compose.setContent {
            bookScreen(
                books = listOf(
                    book(id = 1, title = "읽는 중인 책"),
                    book(id = 2, title = "다 읽은 책", status = BookStatus.FINISHED)
                )
            )
        }

        compose.onNodeWithText("읽고 있는 책 1권").assertIsDisplayed()
        compose.onNodeWithText("읽은 책 1권").assertIsDisplayed()
    }

    @Test
    fun theProgressIsShownWhenTheTotalIsKnown() {
        compose.setContent {
            bookScreen(books = listOf(book(id = 1, title = "쪽수 있는 책", totalPages = 200, currentPage = 50)))
        }

        compose.onNodeWithText("50 / 200쪽 · 25%").assertIsDisplayed()
    }

    @Test
    fun withoutATotalOnlyTheCurrentPageIsShown() {
        compose.setContent {
            bookScreen(books = listOf(book(id = 1, title = "쪽수 모르는 책", currentPage = 50)))
        }

        // 전체를 모르는데 "0%"라고 적으면 안 읽은 것처럼 보입니다.
        compose.onNodeWithText("50쪽까지").assertIsDisplayed()
    }

    @Test
    fun addingABookNeedsATitle() {
        compose.setContent { bookScreen(books = emptyList()) }

        compose.onNodeWithTag(bookAddButtonTag).performClick()
        compose.onNodeWithTag(bookSaveButtonTag).assertIsNotEnabled()

        compose.onNodeWithTag(bookTitleInputTag).performTextInput("새 책")
        compose.onNodeWithTag(bookSaveButtonTag).performClick()
    }

    @Test
    fun extractingAQuotePassesTheTextAndPage() {
        var extracted: Triple<Long, String, Int>? = null
        compose.setContent {
            bookScreen(
                books = listOf(book(id = 7, title = "뽑을 책", totalPages = 300, currentPage = 40)),
                onExtractQuote = { id, body, page -> extracted = Triple(id, body, page) }
            )
        }

        compose.onNodeWithTag(bookExtractButtonTag(7)).performClick()
        compose.onNodeWithTag(bookQuoteBodyTag).performTextInput("행동이 먼저다.")
        compose.onNodeWithTag(bookQuoteSaveTag).performClick()

        assertEquals(7L, extracted?.first)
        assertEquals("행동이 먼저다.", extracted?.second)
        // 쪽을 안 고치면 지금 읽는 쪽이 기본값입니다. 매번 적게 하면 뽑기가 번거로워집니다.
        assertEquals(40, extracted?.third)
    }

    @Test
    fun anEmptyQuoteCannotBeSaved() {
        compose.setContent { bookScreen(books = listOf(book(id = 1, title = "책"))) }

        compose.onNodeWithTag(bookExtractButtonTag(1)).performClick()

        compose.onNodeWithTag(bookQuoteSaveTag).assertIsNotEnabled()
    }

    @Test
    fun theQuoteCountFromThisBookIsShown() {
        compose.setContent {
            bookScreen(
                books = listOf(book(id = 1, title = "출처 있는 책", syncId = "book-1")),
                quoteCountBySyncId = mapOf("book-1" to 3)
            )
        }

        compose.onNodeWithText("이 책에서 뽑은 글귀 3개").assertIsDisplayed()
    }

    @Test
    fun recordingProgressPassesTheTypedPage() {
        var progress: Pair<Long, Int>? = null
        compose.setContent {
            bookScreen(
                books = listOf(book(id = 5, title = "진도 책", totalPages = 300)),
                onUpdateProgress = { id, page -> progress = id to page }
            )
        }

        compose.onNodeWithTag(bookProgressButtonTag(5)).performClick()
        compose.onNodeWithText("읽은 쪽").performTextInput("77")
        compose.onNodeWithText("저장").performClick()

        assertEquals(5L to 77, progress)
    }

    private fun book(
        id: Long,
        title: String,
        syncId: String = "book-$id",
        totalPages: Int = 0,
        currentPage: Int = 0,
        status: BookStatus = BookStatus.READING
    ) = BookEntity(
        id = id,
        syncId = syncId,
        title = title,
        totalPages = totalPages,
        currentPage = currentPage,
        status = status.name
    )

    @androidx.compose.runtime.Composable
    private fun bookScreen(
        books: List<BookEntity>,
        quoteCountBySyncId: Map<String, Int> = emptyMap(),
        onSaveBook: (BookDraft) -> Unit = {},
        onUpdateProgress: (Long, Int) -> Unit = { _, _ -> },
        onExtractQuote: (Long, String, Int) -> Unit = { _, _, _ -> }
    ) {
        BookScreen(
            books = books,
            quoteCountBySyncId = quoteCountBySyncId,
            onSaveBook = onSaveBook,
            onUpdateProgress = onUpdateProgress,
            onToggleFinished = {},
            onDeleteBook = {},
            onExtractQuote = onExtractQuote
        )
    }
}
