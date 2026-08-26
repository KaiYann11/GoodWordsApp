package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.codex.appgoodwords.data.ContentDraft
import com.codex.appgoodwords.data.ContentType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 번뜩인 것을 담는 자리.
 *
 * 할 일에 적어 두지 않는 이유가 있습니다. 할 일은 하기로 한 것이고 아이디어는 아직 아무것도
 * 아닌데, 섞으면 할 일 목록이 "안 한 것들"로 불어나 목록 자체를 믿을 수 없게 됩니다.
 *
 * 보관함에 두는 이유도 있습니다. 번뜩인 것의 핵심 성질은 **나중에 다시 만나야 한다**는 것인데,
 * 그 일을 하는 장치가 보관함에만 있습니다.
 */
class IdeaCaptureTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun anIdeaIsSavedAsItsOwnKind() {
        var saved: ContentDraft? = null
        compose.setContent { addScreen(onSave = { saved = it }) }

        compose.onNodeWithText("제목", substring = true).performTextInput("자기 전 5분 회고")
        scrollTo(ideaSwitchTag)
        compose.onNodeWithTag(ideaSwitchTag).performClick()
        scrollTo(ideaSwitchTag)
        compose.onNodeWithText("담기", substring = true).performClick()

        // 글귀로 담기면 보관함에서 아이디어만 골라 볼 수 없습니다.
        assertEquals(ContentType.IDEA, saved?.type)
    }

    @Test
    fun leavingItOffStillSavesAQuote() {
        var saved: ContentDraft? = null
        compose.setContent { addScreen(onSave = { saved = it }) }

        compose.onNodeWithText("제목", substring = true).performTextInput("좋은 말")
        scrollTo(ideaSwitchTag)
        compose.onNodeWithText("담기", substring = true).performClick()

        // 켜지 않으면 예전 그대로입니다. 주소가 있으면 링크·영상으로 정해집니다.
        assertEquals(ContentType.QUOTE, saved?.type)
    }

    @Test
    fun theSwitchSaysWhereItGoes() {
        // 스위치만 있으면 왜 할 일이 아니라 여기인지 알 수 없습니다.
        compose.setContent { addScreen() }

        scrollTo(ideaSwitchTag)

        compose.onNodeWithText("내가 번뜩인 것").assertIsDisplayed()
        compose.onNodeWithText("할 일이 아니라 여기에 둡니다", substring = true).assertIsDisplayed()
    }


    @androidx.compose.runtime.Composable
    private fun addScreen(onSave: (ContentDraft) -> Unit = {}) {
        AddContentScreen(
            categories = emptyList(),
            existingTags = emptyList(),
            sharedText = null,
            initialDraft = null,
            formVersion = 0,
            submitLabel = "담기",
            secondaryActionLabel = null,
            onSecondaryAction = null,
            onSharedTextConsumed = {},
            onSave = onSave,
            onFetchMetadata = { _, _ -> }
        )
    }

    /**
     * 담는 화면은 세로로 긴 목록이라, 안 보이는 줄은 굴려야 짚힙니다.
     * 화면에는 가로로 굴러가는 칩 줄도 있어서 세로 목록만 골라야 합니다.
     */
    private fun scrollTo(tag: String) {
        compose.onNode(
            hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        ).performScrollToNode(hasTestTag(tag))
    }
}
