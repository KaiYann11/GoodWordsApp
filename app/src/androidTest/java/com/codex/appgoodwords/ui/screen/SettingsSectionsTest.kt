package com.codex.appgoodwords.ui.screen

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.codex.appgoodwords.data.AiFeedbackSettings
import com.codex.appgoodwords.data.AiProvider
import com.codex.appgoodwords.data.ReminderSettings
import com.codex.appgoodwords.data.ServerSyncSettings
import com.codex.appgoodwords.data.SyncStatus
import com.codex.appgoodwords.ui.theme.AppGoodWordsTheme
import org.junit.Rule
import org.junit.Test

/**
 * 설정의 갈래.
 *
 * 한 화면에 카드 열다섯 개가 이어져 있어 어디에 무엇이 있는지 눈에 들어오지 않았습니다. 첫 화면은 갈래 목록이고,
 * 누르면 그 갈래 화면이 열립니다.
 */
class SettingsSectionsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theFirstScreenListsTheSectionsWithTheirState() {
        showSettings(
            reminders = ReminderSettings(remindersEnabled = true, intervalMinutes = 120),
            ai = AiFeedbackSettings(coachEnabled = true, coachGapMinutes = 30).withProvider(AiProvider.OLLAMA)
        )

        SettingsSection.entries.forEach { section ->
            compose.onNodeWithTag(settingsSectionTag(section)).assertIsDisplayed()
        }
        // 들어가 보지 않고도 무엇이 켜져 있는지 압니다.
        compose.onNodeWithText("주기 알림 2시간마다", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Ollama · 한마디 30분마다", substring = true).assertIsDisplayed()
        // 갈래 안의 카드는 아직 없습니다.
        compose.onNodeWithText("반복 노출").assertDoesNotExist()
    }

    @Test
    fun aSectionOpensAndBackReturnsToTheListNotOutOfSettings() {
        showSettings()

        compose.onNodeWithTag(settingsSectionTag(SettingsSection.NOTIFICATIONS)).performClick()
        compose.onNodeWithText("반복 노출").assertIsDisplayed()
        compose.onNodeWithTag(settingsSectionTag(SettingsSection.AI)).assertDoesNotExist()

        // 휴대폰의 뒤로 가기는 설정을 닫지 않고 갈래 목록으로 돌아옵니다.
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag(settingsSectionTag(SettingsSection.AI)).assertIsDisplayed()
        compose.onNodeWithText("반복 노출").assertDoesNotExist()
    }

    @Test
    fun aBlockedNotificationShowsOnTheListItself() {
        // 켜 두었는데 돌지 않는 것은 들어가 보기 전에 보여야 합니다.
        showSettings(reminders = ReminderSettings(remindersEnabled = true), notificationsBlocked = true)

        compose.onNodeWithText("알림 권한이 꺼져 있어 알림이 오지 않습니다").assertIsDisplayed()
    }

    @Test
    fun aShortGapWithFewAsksSaysTheDayRunsOutEarly() {
        // 10분마다 하루 다섯 번이면 첫 한마디부터 40분 안에 하루 몫을 다 씁니다. 고른 대로 두되 결과를 알려 줍니다.
        showSettings(
            ai = AiFeedbackSettings(coachEnabled = true, coachAskLimit = 5, coachGapMinutes = 10),
            section = SettingsSection.AI
        )

        // 화면에 보이는 자리는 에뮬레이터에서 직접 눌러 확인했습니다. 여기서는 고른 값에 따라 안내가 생기는지만 봅니다.
        compose.onNodeWithTag(aiCoachShortDayTag).assertExists()
        compose.onNodeWithText("40분 안에 하루 5번을", substring = true).assertExists()
        compose.onNodeWithTag(aiCoachBackgroundSwitchTag).assertExists()
    }

    @Test
    fun aLongEnoughGapNeedsNoWarning() {
        showSettings(
            ai = AiFeedbackSettings(coachEnabled = true, coachAskLimit = 5, coachGapMinutes = 180),
            section = SettingsSection.AI
        )

        compose.onNodeWithTag(aiCoachShortDayTag).assertDoesNotExist()
    }

    /** 실제 앱처럼 갈래를 밖에서 들고 있습니다(`AppGoodWordsApp`). */
    private fun showSettings(
        reminders: ReminderSettings = ReminderSettings(),
        ai: AiFeedbackSettings = AiFeedbackSettings(),
        notificationsBlocked: Boolean = false,
        section: SettingsSection? = null
    ) {
        compose.setContent {
            var current by remember { mutableStateOf(section) }
            AppGoodWordsTheme {
                SettingsScreen(
                    settings = reminders,
                    serverSyncSettings = ServerSyncSettings(),
                    syncStatus = SyncStatus(),
                    categories = emptyList(),
                    syncBackups = emptyList(),
                    syncBackupDirectory = "",
                    notificationsBlocked = notificationsBlocked,
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
                    aiFeedbackSettings = ai,
                    section = current,
                    onSectionChange = { current = it }
                )
            }
        }
    }
}
