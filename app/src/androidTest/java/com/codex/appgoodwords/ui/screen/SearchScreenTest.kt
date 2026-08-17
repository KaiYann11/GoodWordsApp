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
        var opened: Long? = null
        compose.setContent { searchScreen(onOpenQuote = { opened = it }) }

        compose.onNodeWithTag(searchInputTag).performTextInput("행동")
        compose.onNodeWithText("행동에 대하여").performClick()

        assertEquals(1L, opened)
    }

    @androidx.compose.runtime.Composable
    private fun searchScreen(onOpenQuote: (Long) -> Unit = {}) {
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
            onOpenQuote = onOpenQuote
        )
    }
}
