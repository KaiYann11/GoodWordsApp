package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextRange
import com.codex.appgoodwords.data.BookEntity
import com.codex.appgoodwords.ui.theme.AppGoodWordsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 독후감 화면.
 *
 * 두 가지가 핵심입니다 — 쓴 글이 그대로 저장되는지, 그리고 **글 안에서 고른 문장만**
 * 글귀로 뽑히는지. 고른 곳이 없는데 뽑히면 글 전체가 보관함에 들어가고, 되돌리려면
 * 보관함에서 찾아 지워야 합니다.
 */
class BookReviewScreenTest {
    @get:Rule val compose = createComposeRule()

    private val book = BookEntity(
        id = 1,
        syncId = "book-1",
        title = "아주 작은 습관의 힘",
        author = "제임스 클리어",
        currentPage = 120,
        review = "습관은 정체성의 결과다.\n무엇을 하느냐가 아니라 누구인가가 먼저다."
    )

    @Test fun whatIsWrittenIsHandedBackWhenSaved() {
        var saved: String? = null
        compose.setContent {
            AppGoodWordsTheme {
                BookReviewScreen(book = book.copy(review = ""), onSave = { saved = it }, onExtractQuote = { _, _ -> })
            }
        }

        // 아무것도 안 고쳤으면 저장할 것이 없습니다.
        compose.onNodeWithTag(bookReviewSaveTag).assertIsNotEnabled()
        compose.onNodeWithTag(bookReviewFieldTag).performTextInput("오늘 2장을 읽었다.")
        compose.onNodeWithTag(bookReviewSaveTag).assertIsEnabled()
        compose.onNodeWithTag(bookReviewSaveTag).performClick()

        assertEquals("오늘 2장을 읽었다.", saved)
    }

    @Test fun theBookOnTheScreenIsTheOneBeingWrittenAbout() {
        compose.setContent {
            AppGoodWordsTheme {
                BookReviewScreen(book = book, onSave = {}, onExtractQuote = { _, _ -> })
            }
        }

        compose.onNodeWithText(book.title).assertIsDisplayed()
        compose.onNodeWithText(book.displayAuthor).assertIsDisplayed()
        // 열었을 때 쓰던 글이 그대로 있어야 이어 쓸 수 있습니다.
        compose.onNodeWithText(book.review).assertIsDisplayed()
    }

    @Test fun nothingCanBeExtractedUntilSomethingIsSelected() {
        compose.setContent {
            AppGoodWordsTheme {
                BookReviewScreen(book = book, onSave = {}, onExtractQuote = { _, _ -> })
            }
        }

        compose.onNodeWithTag(bookReviewExtractTag).assertIsNotEnabled()
        compose.onNodeWithText("글 안에서 문장을 길게 눌러 고르면", substring = true).assertIsDisplayed()
    }

    @Test fun theSelectedSentenceGoesToTheLibraryWithTheCurrentPage() {
        var extracted: Pair<String, Int>? = null
        compose.setContent {
            AppGoodWordsTheme {
                BookReviewScreen(book = book, onSave = {}, onExtractQuote = { body, page -> extracted = body to page })
            }
        }

        // "습관은 정체성의 결과다." — 첫 문장만 끌어 고른 것과 같습니다(13자).
        compose.onNodeWithTag(bookReviewFieldTag).performSemanticsAction(SemanticsActions.SetSelection) { set ->
            set(0, 13, true)
        }

        compose.onNodeWithText("고른 13자를 글귀로 뽑습니다.").assertIsDisplayed()
        compose.onNodeWithTag(bookReviewExtractTag).performClick()
        // 넣기 전에 한 번 보여 줍니다. 고른 자리가 한 글자 어긋나는 일이 흔합니다.
        compose.onNodeWithTag(bookReviewQuoteBodyTag).assertIsDisplayed()
        compose.onNodeWithTag(bookReviewQuoteSaveTag).performClick()

        assertEquals("습관은 정체성의 결과다.", extracted?.first)
        // 쪽수는 지금 진도를 미리 넣어 둡니다. 대개 방금 읽은 자리입니다.
        assertEquals(120, extracted?.second)
    }

    @Test fun theQuoteCanBeFixedBeforeItIsFiled() {
        var extracted: Pair<String, Int>? = null
        compose.setContent {
            AppGoodWordsTheme {
                BookReviewScreen(book = book, onSave = {}, onExtractQuote = { body, page -> extracted = body to page })
            }
        }

        compose.onNodeWithTag(bookReviewFieldTag).performSemanticsAction(SemanticsActions.SetSelection) { set ->
            set(0, 13, true)
        }
        compose.onNodeWithTag(bookReviewExtractTag).performClick()
        compose.onNodeWithTag(bookReviewQuoteBodyTag).performTextReplacement("습관은 정체성의 결과다")
        compose.onNodeWithTag(bookReviewQuoteSaveTag).performClick()

        assertEquals("습관은 정체성의 결과다", extracted?.first)
    }
}
