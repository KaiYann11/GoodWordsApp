package com.codex.appgoodwords.ui.screen

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.codex.appgoodwords.ui.theme.AppGoodWordsTheme
import org.junit.Rule
import org.junit.Test

/**
 * 보관함의 네 칸.
 *
 * 여섯 칸일 때는 옆으로 밀어야 메모와 임시저장소가 보였습니다. 메모·개념·임시저장소를 노트 한 칸에 모았고,
 * 예전 번호(`LibraryTab.MEMO` 등)는 노트 안 알약을 가리킵니다 — 검색에서 넘어온 사람이 그 자리에 떨어져야 합니다.
 */
class LibraryTabsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theNotesTabHoldsMemosConceptsAndTheInbox() {
        showLibrary()

        // 처음은 글귀입니다. 노트 안 알약은 노트를 열기 전에는 없습니다.
        compose.onNodeWithText("글귀 목록").assertIsDisplayed()
        compose.onNodeWithTag(libraryMemoTabTag).assertDoesNotExist()

        compose.onNodeWithTag(libraryNoteTabTag).performClick()

        // 노트를 처음 열면 메모입니다.
        compose.onNodeWithText("메모 목록").assertIsDisplayed()
        compose.onNodeWithTag(libraryMemoTabTag).assertIsSelected()
        compose.onNodeWithTag(libraryInboxTabTag).performClick()
        compose.onNodeWithText("임시저장소 목록").assertIsDisplayed()
    }

    @Test
    fun theNotesTabReopensOnThePillLastSeen() {
        // 다른 탭에 다녀올 때마다 메모로 돌아가면 보던 것을 다시 찾아 눌러야 합니다.
        showLibrary()

        compose.onNodeWithTag(libraryNoteTabTag).performClick()
        compose.onNodeWithTag(libraryConceptTabTag).performClick()
        compose.onNodeWithTag(libraryBookTabTag).performClick()
        compose.onNodeWithText("책 목록").assertIsDisplayed()
        compose.onNodeWithTag(libraryNoteTabTag).performClick()

        compose.onNodeWithText("개념 목록").assertIsDisplayed()
    }

    @Test
    fun aSearchForAConceptLandsOnTheConceptPill() {
        showLibrary(requestedTab = LibraryTab.CONCEPT)

        compose.onNodeWithText("개념 목록").assertIsDisplayed()
        compose.onNodeWithTag(libraryConceptTabTag).assertIsSelected()
        compose.onNodeWithTag(libraryNoteTabTag).assertIsSelected()
    }

    @Test
    fun thePillsSayHowManyThereAre() {
        // 눌러 보기 전에 빈 목록인지 알 수 있어야 합니다.
        showLibrary(requestedTab = LibraryTab.MEMO, memoCount = 2, conceptCount = 0, inboxCount = 3)

        compose.onNodeWithText("메모 2").assertIsDisplayed()
        compose.onNodeWithText("개념 0").assertIsDisplayed()
        compose.onNodeWithText("임시저장소 3").assertIsDisplayed()
    }

    @Test
    fun aPiledUpInboxShowsOnTheNotesTabFromElsewhere() {
        // 공유로 담은 글은 임시저장소에 쌓입니다. 노트 안에 들어갔다고 그것이 안 보이면 잊힙니다.
        showLibrary(inboxCount = 3)

        compose.onNodeWithTag(libraryNoteTabTag, useUnmergedTree = false).assertTextContains("3")
    }

    private fun showLibrary(
        requestedTab: Int? = null,
        memoCount: Int = 1,
        conceptCount: Int = 0,
        inboxCount: Int = 0
    ) {
        compose.setContent {
            AppGoodWordsTheme {
                LibraryTabsScreen(
                    quoteContent = { Text("글귀 목록") },
                    bookContent = { Text("책 목록") },
                    ideaContent = { Text("아이디어 목록") },
                    requestedTab = requestedTab,
                    inboxCount = inboxCount,
                    memoCount = memoCount,
                    conceptCount = conceptCount,
                    inboxContent = { Text("임시저장소 목록") },
                    memoContent = { Text("메모 목록") },
                    conceptContent = { Text("개념 목록") }
                )
            }
        }
    }
}
