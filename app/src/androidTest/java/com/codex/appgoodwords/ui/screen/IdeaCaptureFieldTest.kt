package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 번뜩인 것을 한 줄로 담는 칸.
 *
 * 담는 화면까지 들어가면 여섯 걸음입니다. 번뜩인 것은 그 사이에 날아갑니다.
 * 여기서 지키려는 것은 **치고 누르면 끝**이라는 것입니다.
 */
class IdeaCaptureFieldTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun typingAndTappingIsTheWholeThing() {
        var captured: String? = null
        compose.setContent { IdeaCaptureField(onCapture = { captured = it }) }

        compose.onNodeWithTag(ideaCaptureFieldTag).performTextInput("자기 전 5분 회고")
        compose.onNodeWithTag(ideaCaptureSaveTag).performClick()

        assertEquals("자기 전 5분 회고", captured)
    }

    @Test
    fun theFieldClearsSoTheNextOneCanFollow() {
        // 연달아 떠오를 때 지우고 시작하지 않아도 됩니다.
        var count = 0
        compose.setContent { IdeaCaptureField(onCapture = { count += 1 }) }

        compose.onNodeWithTag(ideaCaptureFieldTag).performTextInput("첫 번째")
        compose.onNodeWithTag(ideaCaptureSaveTag).performClick()
        compose.onNodeWithTag(ideaCaptureFieldTag).performTextInput("두 번째")
        compose.onNodeWithTag(ideaCaptureSaveTag).performClick()

        assertEquals(2, count)
    }

    @Test
    fun anEmptyLineIsNotCaptured() {
        var captured: String? = null
        compose.setContent { IdeaCaptureField(onCapture = { captured = it }) }

        compose.onNodeWithTag(ideaCaptureSaveTag).assertIsNotEnabled()

        // 공백만 쳐도 담기지 않습니다. 빈 카드가 보관함에 남습니다.
        compose.onNodeWithTag(ideaCaptureFieldTag).performTextInput("   ")
        compose.onNodeWithTag(ideaCaptureSaveTag).performClick()

        assertEquals(null, captured)
    }

    @Test
    fun surroundingSpaceIsTrimmed() {
        var captured: String? = null
        compose.setContent { IdeaCaptureField(onCapture = { captured = it }) }

        compose.onNodeWithTag(ideaCaptureFieldTag).performTextInput("  띄어쓴 것  ")
        compose.onNodeWithTag(ideaCaptureSaveTag).performClick()

        assertEquals("띄어쓴 것", captured)
    }

    @Test
    fun clearingTheTextDisablesSavingAgain() {
        compose.setContent { IdeaCaptureField(onCapture = {}) }

        compose.onNodeWithTag(ideaCaptureFieldTag).performTextInput("무엇")
        compose.onNodeWithTag(ideaCaptureFieldTag).performTextClearance()

        compose.onNodeWithTag(ideaCaptureSaveTag).assertIsNotEnabled()
    }
}
