package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import com.codex.appgoodwords.AppGoodWordsApplication
import com.codex.appgoodwords.MainActivity
import com.codex.appgoodwords.data.ContentDraft
import com.codex.appgoodwords.data.ContentType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test

/**
 * 담는 칸·고치는 칸은 열 때마다 새로 시작합니다. 앱은 경로마다 화면 상태를 저장해 두므로(`rememberSaveableStateHolder`),
 * 1.22.0까지는 메모를 저장하고 "직접 쓰기"를 다시 누르면 방금 쓴 글과 고른 사진이 그대로 떠 같은 메모를 또 담게 됐고,
 * 고치다 취소한 글이 다음 고치기에 되살아났습니다. 에뮬레이터 전용입니다(실제 화면·Room).
 */
class FreshFormFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AppGoodWordsApplication).container
    private val mark = "fresh-${System.nanoTime()}"
    private val bodyField = hasSetTextAction() and hasText("본문")

    @After fun cleanup() = runBlocking {
        container.database.contentItemDao().getAll()
            .filter { mark in it.title || mark in it.body }
            .forEach { container.repository.deleteContent(it.id) }
    }

    @Test fun aSecondDirectWriteAfterSavingAMemoStartsEmpty() {
        openMemos()
        compose.onNodeWithTag(collectionAddTag).performClick()
        compose.onNode(bodyField).performTextInput("$mark first memo")
        compose.onNodeWithText("저장하기").performScrollTo().performClick()
        compose.waitUntil(10_000) { runBlocking { container.database.contentItemDao().getAll().any { it.body == "$mark first memo" } } }

        compose.onNodeWithTag(collectionAddTag).performClick()
        compose.waitForIdle()

        compose.onNode(bodyField).assert(hasText(mark, substring = true).not())
    }

    @Test fun editingAgainAfterCancellingStartsFromTheSavedMemo() {
        runBlocking { container.repository.saveContent(ContentDraft(type = ContentType.MEMO, title = "$mark title", body = "$mark saved body")) }
        openMemos()
        compose.onNodeWithText("$mark saved body").performClick()
        edit()
        compose.onNode(bodyField).performTextReplacement("$mark abandoned")
        compose.onNodeWithText("수정 취소").performScrollTo().performClick()

        edit()

        compose.onNode(bodyField).assert(hasText("$mark saved body", substring = true))
        compose.onNode(bodyField).assert(hasText("$mark abandoned", substring = true).not())
    }

    private fun openMemos() {
        val libraryTab = hasText("보관함") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        // 앱은 잠금 여부를 읽는 동안 빈 화면입니다(AppLockState.CHECKING). 그 읽기는 시험이 기다려 주지 않습니다.
        compose.waitUntil(10_000) { compose.onAllNodes(libraryTab).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(libraryTab).performClick()
        compose.onNodeWithTag(libraryNoteTabTag).performClick()
        compose.onNodeWithTag(libraryMemoTabTag).performClick()
    }

    private fun edit() {
        compose.onNodeWithTag(detailListTag).performScrollToNode(hasText("편집"))
        compose.onNodeWithText("편집").performClick()
        compose.waitForIdle()
    }
}
