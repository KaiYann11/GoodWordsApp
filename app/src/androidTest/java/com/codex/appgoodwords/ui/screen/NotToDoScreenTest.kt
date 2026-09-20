package com.codex.appgoodwords.ui.screen

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.codex.appgoodwords.data.ContentDraft
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ContentType
import com.codex.appgoodwords.ui.theme.AppGoodWordsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NotToDoScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun aFailedSaveKeepsTheDraftAndCanBeRetried() {
        var saved: ContentDraft? = null
        var fail = true
        compose.setContent {
            AppGoodWordsTheme {
                NotToDoScreen(emptyList(), onDelete = { Result.success(Unit) }, onSave = {
                    if (fail) Result.failure(IllegalStateException("저장 공간을 확인해 주세요."))
                    else { saved = it; Result.success(Unit) }
                })
            }
        }
        compose.onNodeWithTag(notToDoAddTag).performClick()
        compose.onNodeWithText("저장").assertIsNotEnabled()
        compose.onNodeWithTag(notToDoTitleTag).performTextInput("충동 구매")
        compose.onNodeWithTag(notToDoMemoTag).performTextInput("하루 기다리기")
        compose.onNodeWithText("저장").performClick()
        compose.onNodeWithText("저장 공간을 확인해 주세요.").assertIsDisplayed()
        compose.onNodeWithText("충동 구매").assertIsDisplayed()
        compose.onNodeWithText("하루 기다리기").assertIsDisplayed()
        fail = false
        compose.onNodeWithText("저장").performClick()
        compose.onNodeWithTag(notToDoTitleTag).assertDoesNotExist()
        assertEquals(ContentType.NOT_TO_DO, saved?.type)
        assertEquals("충동 구매", saved?.title)
        assertEquals("하루 기다리기", saved?.body)
    }

    @Test fun cancellingOrFailingDeletionKeepsTheRecord() {
        val item = ContentItemEntity(id = 1, type = ContentType.NOT_TO_DO, title = "늦게 커피 마시기", body = "숙면을 위해")
        val records = mutableStateOf(listOf(item))
        var attempts = 0
        compose.setContent {
            AppGoodWordsTheme {
                NotToDoScreen(records.value, onSave = { Result.success(Unit) }, onDelete = {
                    attempts++
                    if (attempts == 1) Result.failure(IllegalStateException("삭제 실패"))
                    else { records.value = emptyList(); Result.success(Unit) }
                })
            }
        }
        compose.onNodeWithTag("not_to_do_delete_1").performClick()
        compose.onNodeWithText("취소").performClick()
        assertEquals(0, attempts)
        compose.onNodeWithText(item.title).assertIsDisplayed()
        compose.onNodeWithTag("not_to_do_delete_1").performClick()
        compose.onNodeWithTag("not_to_do_confirm_delete").performClick()
        compose.onNodeWithText("삭제 실패").assertIsDisplayed()
        compose.onNodeWithTag("not_to_do_confirm_delete").performClick()
        compose.onNodeWithText(item.title).assertDoesNotExist()
        assertEquals(2, attempts)
    }

    @Test fun theTwoTabsRemainSeparateAndSearchCanRequestEitherOne() {
        val request = mutableStateOf<Boolean?>(null)
        val requestKey = mutableStateOf(0)
        compose.setContent {
            AppGoodWordsTheme {
                RoutineTabsScreen(
                    routineContent = { Text("실천할 루틴 목록") },
                    notToDoContent = { Text("피할 행동 목록") },
                    requestedNotToDo = request.value, requestKey = requestKey.value
                )
            }
        }
        compose.onNodeWithText("실천할 루틴 목록").assertIsDisplayed()
        compose.onNodeWithTag(notToDoTabTag).performClick()
        compose.onNodeWithText("피할 행동 목록").assertIsDisplayed()
        compose.onNodeWithText("실천할 루틴 목록").assertDoesNotExist()
        compose.runOnIdle { request.value = false; requestKey.value++ }
        compose.onNodeWithText("실천할 루틴 목록").assertIsDisplayed()
        compose.runOnIdle { request.value = true; requestKey.value++ }
        compose.onNodeWithText("피할 행동 목록").assertIsDisplayed()
    }
}
