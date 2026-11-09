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
    fun finishingWithoutPagesShowsTheReadingDayOnTheCard() {
        compose.setContent {
            bookScreen(
                books = listOf(book(id = 1, title = "쪽수 없는 완독", status = BookStatus.FINISHED)),
                readingDaysBySyncId = mapOf("book-1" to 1)
            )
        }
        compose.onNodeWithText("다 읽음 · 쪽수 모름").assertIsDisplayed()
        compose.onNodeWithText("읽은 날 1일").assertIsDisplayed()
        compose.onNodeWithText("0쪽", substring = true).assertDoesNotExist()
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

        compose.onNodeWithText("뽑은 글귀 3개").assertIsDisplayed()
    }

    @Test
    fun recordingProgressPassesTheTypedPage() {
        var progress: Triple<Long, Int, String>? = null
        compose.setContent {
            bookScreen(
                books = listOf(book(id = 5, title = "진도 책", totalPages = 300)),
                onUpdateProgress = { id, page, note -> progress = Triple(id, page, note) }
            )
        }

        compose.onNodeWithTag(bookProgressButtonTag(5)).performClick()
        compose.onNodeWithText("읽은 쪽").performTextInput("77")
        compose.onNodeWithTag(bookProgressSaveTag).performClick()

        // 남길 말은 고르는 것입니다. 안 적으면 독후감은 그대로입니다.
        assertEquals(Triple(5L, 77, ""), progress)
    }

    @Test
    fun aProgressNoteIsPassedAlong() {
        var progress: Triple<Long, Int, String>? = null
        compose.setContent {
            bookScreen(
                books = listOf(book(id = 5, title = "진도 책", totalPages = 300)),
                onUpdateProgress = { id, page, note -> progress = Triple(id, page, note) }
            )
        }

        compose.onNodeWithTag(bookProgressButtonTag(5)).performClick()
        // 어디에 붙는지 창에서 말해 둡니다. 독후감은 카드에 보이지 않아서, 말하지 않으면 남긴 말이 사라진 것처럼 보입니다.
        compose.onNodeWithText("적으면 날짜·쪽과 함께 독후감 끝에 붙습니다.").assertIsDisplayed()
        compose.onNodeWithText("읽은 쪽").performTextInput("120")
        compose.onNodeWithTag(bookProgressNoteInputTag).performTextInput("주인공의 선택이 보인다\n오래 남는다")
        compose.onNodeWithTag(bookProgressSaveTag).performClick()

        // 줄바꿈은 적은 그대로 넘깁니다. 다듬는 것은 BookNotes 한 곳입니다.
        assertEquals(Triple(5L, 120, "주인공의 선택이 보인다\n오래 남는다"), progress)
    }

    @Test
    fun aLongMemoShowsTheLastTwoLinesUntilOpened() {
        val note = listOf(
            "11월 1일 · 10쪽 — 첫 기록",
            "11월 3일 · 40쪽 — 둘째 기록",
            "11월 5일 · 96쪽 — 초반은 느리다",
            "11월 8일 · 120쪽 — 주인공의 선택"
        ).joinToString("\n")
        compose.setContent { bookScreen(books = listOf(book(id = 3, title = "메모 쌓인 책", note = note))) }

        // 끝의 두 줄만 보이고 앞의 것은 접혀 있습니다. 쌓일수록 카드가 길어지면 다른 책이 밀려납니다.
        compose.onNodeWithText("11월 5일 · 96쪽 — 초반은 느리다").assertIsDisplayed()
        compose.onNodeWithText("11월 8일 · 120쪽 — 주인공의 선택").assertIsDisplayed()
        compose.onNodeWithText("첫 기록", substring = true).assertDoesNotExist()
        compose.onNodeWithText("메모 전체 보기 (4줄)").assertIsDisplayed()

        compose.onNodeWithTag(bookNoteToggleTag(3)).performClick()

        compose.onNodeWithText("첫 기록", substring = true).assertIsDisplayed()
        compose.onNodeWithText("접기").assertIsDisplayed()
    }

    @Test
    fun aShortMemoHasNothingToOpen() {
        compose.setContent { bookScreen(books = listOf(book(id = 3, title = "책", note = "11월 8일 · 120쪽 — 좋다"))) }

        compose.onNodeWithText("11월 8일 · 120쪽 — 좋다").assertIsDisplayed()
        compose.onNodeWithTag(bookNoteToggleTag(3)).assertDoesNotExist()
    }

    private fun book(
        id: Long,
        title: String,
        syncId: String = "book-$id",
        totalPages: Int = 0,
        currentPage: Int = 0,
        status: BookStatus = BookStatus.READING,
        note: String = ""
    ) = BookEntity(
        id = id,
        syncId = syncId,
        title = title,
        totalPages = totalPages,
        currentPage = currentPage,
        status = status.name,
        note = note
    )

    @androidx.compose.runtime.Composable
    private fun bookScreen(
        books: List<BookEntity>,
        quoteCountBySyncId: Map<String, Int> = emptyMap(),
        readingDaysBySyncId: Map<String, Int> = emptyMap(),
        onSaveBook: (BookDraft) -> Unit = {},
        onUpdateProgress: (Long, Int, String) -> Unit = { _, _, _ -> },
        onExtractQuote: (Long, String, Int) -> Unit = { _, _, _ -> }
    ) {
        BookScreen(
            books = books,
            quoteCountBySyncId = quoteCountBySyncId,
            readingDaysBySyncId = readingDaysBySyncId,
            onSaveBook = onSaveBook,
            onUpdateProgress = onUpdateProgress,
            onToggleFinished = {},
            onDeleteBook = {},
            onExtractQuote = onExtractQuote,
            onOpenReview = {},
            onOpenProgress = {}
        )
    }
}
