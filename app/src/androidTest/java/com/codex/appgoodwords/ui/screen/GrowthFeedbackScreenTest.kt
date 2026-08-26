package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.codex.appgoodwords.data.GrowthReportEntity
import com.codex.appgoodwords.data.PendingGrowthPrompt
import com.codex.appgoodwords.data.ReportPeriod
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * AI에 못 붙어도 돌아보기를 남길 수 있어야 합니다.
 *
 * 열쇠가 없거나 서버가 꺼져 있어도, 사용자는 자기 계정으로 얼마든지 물어볼 수 있습니다.
 * 그렇게 받아 온 답을 앱에 남길 길이 없으면 이 화면은 열쇠가 있는 사람만의 것이 됩니다.
 */
class GrowthFeedbackScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theWayOutIsOnTheScreenEvenWithoutAKey() {
        compose.setContent { growthScreen() }

        compose.onNodeWithTag(growthPasteButtonTag).assertIsDisplayed()
        // 무엇을 하라는 것인지 버튼만 보고는 알기 어렵습니다.
        compose.onNodeWithText("AI 연결이 안 되면", substring = true).assertExists()
    }

    @Test
    fun theAnswerGoesInAndComesBackOut() {
        var saved = ""
        compose.setContent { growthScreen(onSaveAnswer = { saved = it }) }

        compose.onNodeWithTag(growthPasteButtonTag).performClick()
        compose.onNodeWithTag(growthAnswerFieldTag).performTextInput("""{"guide":"천천히"}""")
        compose.onNodeWithText("저장").performClick()

        assertEquals("""{"guide":"천천히"}""", saved)
    }

    @Test
    fun anEmptyAnswerCannotBeSaved() {
        // 빈 글을 저장하면 아무것도 없는 카드가 목록에 남습니다.
        compose.setContent { growthScreen() }

        compose.onNodeWithTag(growthPasteButtonTag).performClick()

        compose.onNodeWithText("저장").assertIsNotEnabled()
    }

    @Test
    fun thePasteDialogSaysWhichStretchItWillBeFiledUnder() {
        // 어느 구간의 답인지 모르고 저장하면, 나중에 그 문장이 무엇을 보고 나온 것인지 알 수 없습니다.
        compose.setContent {
            growthScreen(
                pendingPrompt = PendingGrowthPrompt(ReportPeriod.WEEKLY, LocalDate.of(2026, 8, 26))
            )
        }

        compose.onNodeWithTag(growthPasteButtonTag).performClick()

        compose.onNodeWithText("2026-08-20 ~ 2026-08-26", substring = true).assertExists()
    }

    @Test
    fun theBlockToCopyIsTheBlockThatWouldBeSent() {
        var copiedFor: ReportPeriod? = null
        compose.setContent {
            growthScreen(
                previewText = "코치입니다\n\n기간: 2026-08-20 ~ 2026-08-26",
                onPromptCopied = { copiedFor = it }
            )
        }

        compose.onNodeWithText("코치입니다", substring = true).assertExists()
        compose.onNodeWithText("복사").performClick()

        // 무엇을 복사해 갔는지 적어 두어야 돌아와서 답을 넣을 자리를 압니다.
        assertTrue(copiedFor.toString(), copiedFor == ReportPeriod.WEEKLY)
    }

    @androidx.compose.runtime.Composable
    private fun growthScreen(
        reports: List<GrowthReportEntity> = emptyList(),
        previewText: String? = null,
        pendingPrompt: PendingGrowthPrompt? = null,
        onSaveAnswer: (String) -> Unit = {},
        onPromptCopied: (ReportPeriod) -> Unit = {}
    ) {
        GrowthFeedbackScreen(
            reports = reports,
            running = false,
            errorMessage = "",
            onRequest = {},
            onPreview = {},
            onDeleteReport = {},
            onKeepQuote = {},
            onKeepRoutine = {},
            previewText = previewText,
            pendingPrompt = pendingPrompt,
            onSaveAnswer = onSaveAnswer,
            onPromptCopied = onPromptCopied
        )
    }
}
