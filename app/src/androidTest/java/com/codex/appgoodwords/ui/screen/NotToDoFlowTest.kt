package com.codex.appgoodwords.ui.screen

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import com.codex.appgoodwords.AppGoodWordsApplication
import com.codex.appgoodwords.MainActivity
import com.codex.appgoodwords.data.ContentType
import com.codex.appgoodwords.data.ExposureTrigger
import com.codex.appgoodwords.data.SyncEntityType
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 실제 화면에서 작성한 기록을 Room에 저장하고 다시 열어 확인합니다. 에뮬레이터 전용입니다. */
class NotToDoFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AppGoodWordsApplication).container
    private val title = "시험-${System.nanoTime()} 충동 구매"

    @After fun cleanup() = runBlocking {
        container.database.contentItemDao().getAll().filter { it.title.startsWith(title) }
            .forEach { container.repository.deleteContent(it.id) }
    }

    @Test fun createReopenSearchEditAndDeletePreserveTheRecordIdentity() {
        compose.onNode(hasText("루틴") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
        compose.onNodeWithTag(notToDoTabTag).performClick()
        compose.onNodeWithTag(notToDoAddTag).performClick()
        compose.onNodeWithTag(notToDoTitleTag).performTextInput(title)
        compose.onNodeWithText("저장").performClick()
        compose.waitUntil(10_000) { runBlocking { container.database.contentItemDao().getAll().any { it.title == title } } }
        compose.onNodeWithText(title).assertIsDisplayed()
        val original = runBlocking { container.database.contentItemDao().getAll().single { it.title == title } }
        assertEquals(ContentType.NOT_TO_DO, original.type)
        assertEquals("", original.body)

        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithContentDescription("검색").performClick()
        compose.onNodeWithTag(searchInputTag).performTextInput(title)
        compose.onNode(hasText(title) and SemanticsMatcher.keyNotDefined(SemanticsProperties.EditableText)).performClick()
        compose.onNodeWithTag("not_to_do_item_${original.id}").assertIsDisplayed()

        compose.onNodeWithTag("not_to_do_edit_${original.id}").performClick()
        compose.onNodeWithTag(notToDoTitleTag).performTextReplacement("$title 줄이기")
        compose.onNodeWithTag(notToDoMemoTag).performTextInput("하루 기다리기\n필요한지 다시 생각하기")
        compose.onNodeWithText("저장").performClick()
        compose.waitUntil(10_000) { runBlocking { container.database.contentItemDao().getById(original.id)?.body?.isNotBlank() == true } }
        runBlocking {
            val edited = container.database.contentItemDao().getById(original.id)!!
            assertEquals(original.syncId, edited.syncId)
            assertEquals(ContentType.NOT_TO_DO, edited.type)
            assertEquals("하루 기다리기\n필요한지 다시 생각하기", edited.body)
            container.repository.recordContentViewed(edited.id, ExposureTrigger.DETAIL_OPEN)
            assertFalse(container.repository.markContentConfirmed(edited.id, ExposureTrigger.DETAIL_CHECK))
            assertTrue(container.database.exposureEventDao().getAll().none { it.contentItemId == edited.id })
        }
        compose.waitForIdle()
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(container.appContext.getExternalFilesDir(null), "not-to-do-ui.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
        compose.onNodeWithTag("not_to_do_delete_${original.id}").performClick()
        compose.onNodeWithText("취소").performClick()
        compose.onNodeWithTag("not_to_do_item_${original.id}").assertIsDisplayed()
        compose.onNodeWithTag("not_to_do_delete_${original.id}").performClick()
        compose.onNodeWithTag("not_to_do_confirm_delete").performClick()
        compose.waitUntil(10_000) { runBlocking { container.database.contentItemDao().getById(original.id) == null } }
        runBlocking {
            assertTrue(container.database.deletionDao().getAll().any {
                it.syncId == original.syncId && it.entityType == SyncEntityType.CONTENT_ITEM
            })
        }
    }
}
