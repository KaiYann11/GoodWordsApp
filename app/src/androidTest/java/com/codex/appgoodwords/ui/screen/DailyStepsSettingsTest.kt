package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.codex.appgoodwords.data.DailyStep
import com.codex.appgoodwords.data.ReminderSettings
import com.codex.appgoodwords.data.ServerSyncSettings
import com.codex.appgoodwords.data.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 하루의 축을 고르는 자리.
 *
 * 예전에는 글귀·루틴·일기 셋으로 박혀 있었습니다. 할 일을 끝내는 것이 하루인 사람에게는
 * 첫 칸이 늘 남의 것이었고, 채울 수 없는 하나가 남아 이어 온 날이 매일 끊겼습니다.
 */
class DailyStepsSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aStepCanBeAddedToTheDay() {
        var chosen: List<DailyStep>? = null
        compose.setContent { settingsScreen(onChanged = { chosen = it }) }

        scrollTo(dailyStepToggleTag(DailyStep.TODO))
        compose.onNodeWithTag(dailyStepToggleTag(DailyStep.TODO)).performClick()

        assertEquals(DailyStep.DEFAULTS + DailyStep.TODO, chosen)
    }

    @Test
    fun aStepCanBeDropped() {
        var chosen: List<DailyStep>? = null
        compose.setContent { settingsScreen(onChanged = { chosen = it }) }

        scrollTo(dailyStepToggleTag(DailyStep.QUOTE))
        compose.onNodeWithTag(dailyStepToggleTag(DailyStep.QUOTE)).performClick()

        // 글귀로 하루를 열지 않는 사람도 있습니다.
        assertEquals(listOf(DailyStep.ROUTINE, DailyStep.DIARY), chosen)
    }

    @Test
    fun theLastStepCannotBeDropped() {
        // 하나도 안 남기면 카드가 뜻을 잃습니다.
        compose.setContent { settingsScreen(steps = listOf(DailyStep.ROUTINE)) }

        scrollTo(dailyStepToggleTag(DailyStep.ROUTINE))
        compose.onNodeWithTag(dailyStepToggleTag(DailyStep.ROUTINE)).assertIsNotEnabled()
    }

    @Test
    fun theOrderOnTheCardIsTheOrderChosenHere() {
        var chosen: List<DailyStep>? = null
        compose.setContent { settingsScreen(onChanged = { chosen = it }) }

        scrollTo(dailyStepUpTag(DailyStep.ROUTINE))
        compose.onNodeWithTag(dailyStepUpTag(DailyStep.ROUTINE)).performClick()

        assertEquals(listOf(DailyStep.ROUTINE, DailyStep.QUOTE, DailyStep.DIARY), chosen)
    }

    @Test
    fun theFirstStepHasNowhereToGoUp() {
        compose.setContent { settingsScreen() }

        scrollTo(dailyStepUpTag(DailyStep.QUOTE))
        compose.onNodeWithTag(dailyStepUpTag(DailyStep.QUOTE)).assertIsNotEnabled()
    }

    /**
     * 설정은 LazyColumn이라 안 보이는 줄은 아예 만들어지지 않습니다. 굴려 놓아야 짚을 수 있습니다.
     * 화면에는 가로로 굴러가는 칩 목록도 있어서 세로 목록만 골라야 합니다.
     */
    private fun scrollTo(tag: String) {
        compose.onNode(
            hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        ).performScrollToNode(hasTestTag(tag))
    }

    @androidx.compose.runtime.Composable
    private fun settingsScreen(
        steps: List<DailyStep> = DailyStep.DEFAULTS,
        onChanged: (List<DailyStep>) -> Unit = {}
    ) {
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
            dailySteps = steps,
            onDailyStepsChanged = onChanged
        )
    }
}
