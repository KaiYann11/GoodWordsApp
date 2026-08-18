package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.codex.appgoodwords.data.BookEntity
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ContentType
import com.codex.appgoodwords.data.DiaryEntity
import com.codex.appgoodwords.data.RoutineEntity
import com.codex.appgoodwords.data.SearchHit
import com.codex.appgoodwords.data.SearchKind
import com.codex.appgoodwords.data.TodoEntity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 한 곳에서 다섯 기능을 다 찾을 수 있는지 확인합니다.
 */
class SearchScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun typingFindsAcrossKinds() {
        compose.setContent { searchScreen() }

        compose.onNodeWithTag(searchInputTag).performTextInput("여름")

        compose.onNodeWithText("일기 1").assertIsDisplayed()
        compose.onNodeWithText("바다").assertIsDisplayed()
    }

    @Test
    fun nothingIsShownBeforeTyping() {
        compose.setContent { searchScreen() }

        // 빈칸에 전부를 쏟아 내면 검색 화면이 목록 화면이 됩니다.
        compose.onNodeWithText("일기 1").assertDoesNotExist()
        compose.onNodeWithText("글귀 1").assertDoesNotExist()
    }

    @Test
    fun aMissSaysSo() {
        compose.setContent { searchScreen() }

        compose.onNodeWithTag(searchInputTag).performTextInput("없는말")

        compose.onNodeWithText("\"없는말\"에 맞는 기록이 없습니다.").assertIsDisplayed()
    }

    @Test
    fun oneWordCanTouchSeveralKinds() {
        compose.setContent { searchScreen() }

        compose.onNodeWithTag(searchInputTag).performTextInput("아침")

        compose.onNodeWithText("할 일 1").assertIsDisplayed()
        compose.onNodeWithText("루틴 1").assertIsDisplayed()
    }

    @Test
    fun tappingAQuoteOpensIt() {
        var opened: SearchHit? = null
        compose.setContent { searchScreen(onOpenHit = { opened = it }) }

        compose.onNodeWithTag(searchInputTag).performTextInput("행동")
        compose.onNodeWithText("행동에 대하여").performClick()

        assertEquals(1L, opened?.id)
        assertEquals(SearchKind.QUOTE, opened?.kind)
    }

    // 예전에는 글귀만 눌러서 갈 수 있었습니다. 나머지는 찾아 놓고도 다시 손으로 뒤져야 했습니다.

    @Test
    fun aDiaryCanBeOpened() =
        assertOpens(query = "여름", label = "바다", kind = SearchKind.DIARY, id = 2L)

    @Test
    fun aTodoCanBeOpened() =
        assertOpens(query = "약", label = "아침 약 먹기", kind = SearchKind.TODO, id = 3L)

    @Test
    fun aBookCanBeOpened() =
        assertOpens(query = "몰입의", label = "몰입의 즐거움", kind = SearchKind.BOOK, id = 4L)

    @Test
    fun aRoutineCanBeOpened() =
        assertOpens(query = "산책", label = "아침 산책", kind = SearchKind.ROUTINE, id = 5L)

    /** 검색어와 결과 제목이 똑같으면 검색창까지 함께 걸려서 어느 것을 누를지 정할 수 없습니다. */
    private fun assertOpens(query: String, label: String, kind: SearchKind, id: Long) {
        var opened: SearchHit? = null
        compose.setContent { searchScreen(onOpenHit = { opened = it }) }

        compose.onNodeWithTag(searchInputTag).performTextInput(query)
        compose.onNodeWithText(label).performClick()

        assertEquals("$label 을(를) 눌러도 갈 곳이 없습니다.", kind, opened?.kind)
        assertEquals(id, opened?.id)
    }

    @androidx.compose.runtime.Composable
    private fun searchScreen(onOpenHit: (SearchHit) -> Unit = {}) {
        SearchScreen(
            items = listOf(
                ContentItemEntity(
                    id = 1,
                    syncId = "q1",
                    type = ContentType.QUOTE,
                    title = "행동에 대하여",
                    body = "행동은 감정이 따라올 때까지 기다리면 늘 늦다."
                )
            ),
            diaries = listOf(
                DiaryEntity(id = 2, syncId = "d1", entryDate = "2025-07-14", title = "바다", body = "여름 바다에 다녀왔다.")
            ),
            todos = listOf(
                TodoEntity(id = 3, syncId = "t1", title = "아침 약 먹기", dueDate = "2026-08-18")
            ),
            books = listOf(BookEntity(id = 4, syncId = "b1", title = "몰입의 즐거움", author = "칙센트미하이")),
            routines = listOf(RoutineEntity(id = 5, syncId = "r1", title = "아침 산책")),
            onOpenHit = onOpenHit
        )
    }
}
