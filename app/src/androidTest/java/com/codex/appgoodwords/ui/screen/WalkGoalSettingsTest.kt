package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.codex.appgoodwords.data.ReminderSettings
import com.codex.appgoodwords.data.ServerSyncSettings
import com.codex.appgoodwords.data.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * 걸은 날로 셀 걸음 수를 네 칸 말고 직접 적는 자리(사용자가 정함, 2026-11-11 — "직접" 칩과 작은 창, 1,000~30,000).
 */
class WalkGoalSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aNumberNotOnTheChipsCanBeTyped() {
        var chosen: Int? = null
        compose.setContent { settingsScreen(onChanged = { chosen = it }) }

        scrollTo(walkGoalCustomTag)
        compose.onNodeWithTag(walkGoalCustomTag).performClick()
        compose.onNodeWithTag(walkGoalCustomFieldTag).performTextInput("6500")
        compose.onNodeWithTag(walkGoalCustomSaveTag).performClick()

        assertEquals(6_500, chosen)
    }

    @Test
    fun aNumberOutsideTheRangeCannotBeSaved() {
        var chosen: Int? = null
        compose.setContent { settingsScreen(onChanged = { chosen = it }) }

        scrollTo(walkGoalCustomTag)
        compose.onNodeWithTag(walkGoalCustomTag).performClick()
        compose.onNodeWithTag(walkGoalCustomSaveTag).assertIsNotEnabled()
        compose.onNodeWithTag(walkGoalCustomFieldTag).performTextInput("500")
        compose.onNodeWithTag(walkGoalCustomSaveTag).assertIsNotEnabled()
        compose.onNodeWithText("1,000~30,000 사이로 적어 주세요").assertExists()
        compose.onNodeWithTag(walkGoalCustomFieldTag).performTextInput("00")
        // 50,000은 넘칩니다.
        compose.onNodeWithTag(walkGoalCustomSaveTag).assertIsNotEnabled()
        compose.onNodeWithTag(walkGoalCustomFieldTag).performTextClearance()
        compose.onNodeWithTag(walkGoalCustomFieldTag).performTextInput("8000")
        compose.onNodeWithTag(walkGoalCustomSaveTag).assertIsEnabled()

        assertNull("저장을 누르기 전에는 바뀌지 않습니다", chosen)
    }

    @Test
    fun cancellingKeepsTheGoal() {
        var chosen: Int? = null
        compose.setContent { settingsScreen(onChanged = { chosen = it }) }

        scrollTo(walkGoalCustomTag)
        compose.onNodeWithTag(walkGoalCustomTag).performClick()
        compose.onNodeWithTag(walkGoalCustomFieldTag).performTextInput("6500")
        compose.onNodeWithText("취소").performClick()

        assertNull(chosen)
        compose.onNodeWithTag(walkGoalCustomFieldTag).assertDoesNotExist()
    }

    @Test
    fun aTypedGoalShowsOnItsChip() {
        compose.setContent { settingsScreen(goal = 6_500) }

        scrollTo(walkGoalCustomTag)
        compose.onNodeWithTag(walkGoalCustomTag).assertIsSelected()
        compose.onNodeWithText("직접 6,500").assertExists()
        compose.onNodeWithTag(walkGoalChipTag(5_000)).assertIsNotSelected()
    }

    @Test
    fun aTypedGoalCanBeChangedAgain() {
        var chosen: Int? = null
        compose.setContent { settingsScreen(goal = 6_500, onChanged = { chosen = it }) }

        scrollTo(walkGoalCustomTag)
        compose.onNodeWithTag(walkGoalCustomTag).performClick()
        // 이미 적은 수가 칸에 들어 있습니다.
        compose.onNodeWithTag(walkGoalCustomFieldTag).assertTextContains("6500")
        compose.onNodeWithTag(walkGoalCustomFieldTag).performTextClearance()
        compose.onNodeWithTag(walkGoalCustomFieldTag).performTextInput("12000")
        compose.onNodeWithTag(walkGoalCustomSaveTag).performClick()

        assertEquals(12_000, chosen)
    }

    @Test
    fun aChipStillPicksItsNumber() {
        var chosen: Int? = null
        compose.setContent { settingsScreen(goal = 6_500, onChanged = { chosen = it }) }

        scrollTo(walkGoalChipTag(7_000))
        compose.onNodeWithTag(walkGoalChipTag(7_000)).performClick()

        assertEquals(7_000, chosen)
    }

    /** 설정은 LazyColumn이라 안 보이는 줄은 만들어지지 않습니다. 세로 목록만 골라 굴립니다(DailyStepsSettingsTest와 같게). */
    private fun scrollTo(tag: String) {
        compose.onNode(
            hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        ).performScrollToNode(hasTestTag(tag))
    }

    @androidx.compose.runtime.Composable
    private fun settingsScreen(goal: Int = 5_000, onChanged: (Int) -> Unit = {}) {
        SettingsScreen(
            settings = ReminderSettings(),
            serverSyncSettings = ServerSyncSettings(),
            syncStatus = SyncStatus(),
            categories = emptyList(),
            syncBackups = emptyList(),
            syncBackupDirectory = "",
            onSettingsChanged = {},
            onServerSyncSettingsChanged = {},
            onSendTestNotification = {},
            onResetViewCounts = {},
            onExportRequested = {},
            onImportRequested = {},
            onTestServerConnection = {},
            onSyncWithServer = {},
            onUploadToServer = {},
            onDownloadFromServer = {},
            onRestoreBackup = {},
            onDeleteCategory = {},
            walkGoalSteps = goal,
            onWalkGoalStepsChanged = onChanged,
            section = SettingsSection.STEPS
        )
    }
}
