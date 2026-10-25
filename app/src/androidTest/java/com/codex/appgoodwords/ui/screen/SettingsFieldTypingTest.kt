package com.codex.appgoodwords.ui.screen

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.codex.appgoodwords.data.ReminderSettings
import com.codex.appgoodwords.data.ServerSyncSettings
import com.codex.appgoodwords.data.SyncStatus
import com.codex.appgoodwords.ui.theme.AppGoodWordsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 글자마다 저장하는 설정 칸.
 *
 * 앱에서는 저장된 값이 한 박자 늦게 돌아옵니다. 여기서는 돌아오는 때를 시험이 정해, 치는 도중에 옛 값이
 * 돌아오는 순간을 그대로 만듭니다. 빠르게 친 서버 주소가 글자가 빠지고 뒤섞인 채 저장됐던 일입니다(2026-10-25).
 */
class SettingsFieldTypingTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var reminders by mutableStateOf(ReminderSettings(intervalMinutes = 60))
    private var sync by mutableStateOf(ServerSyncSettings())
    private val sentReminders = mutableListOf<ReminderSettings>()
    private val sentSync = mutableListOf<ServerSyncSettings>()
    private var section by mutableStateOf<SettingsSection?>(null)

    @Test
    fun aLateSaveDoesNotEatWhatIsBeingTyped() {
        showSettings(SettingsSection.SYNC)

        val field = compose.onNodeWithTag(serverUrlFieldTag)
        field.performTextInput("https://a")
        field.performTextInput("b")
        // 앞서 보낸 "https://a"가 이제야 돌아옵니다. 칸에는 이미 b까지 쳤습니다.
        compose.runOnIdle { sync = sentSync.first() }

        field.assert(hasText("https://ab"))
        compose.runOnIdle { assertEquals("https://ab", sentSync.last().serverUrl) }
    }

    @Test
    fun aValueChangedElsewhereShowsOnceTheFieldIsLeft() {
        // 동기화나 가져오기로 바뀐 값은 칸을 떠나면 보입니다. 치는 동안에는 기다립니다.
        showSettings(SettingsSection.SYNC)

        val field = compose.onNodeWithTag(serverUrlFieldTag)
        field.performTextInput("https://a")
        compose.runOnIdle { sync = ServerSyncSettings(serverUrl = "https://elsewhere") }
        field.assert(hasText("https://a"))

        // 다음 칸(API 키)을 누르면 주소 칸은 저장된 값을 보여 줍니다. 사람이 칸을 옮기는 그대로입니다.
        // (포커스를 그냥 걷어 내면 이 화면의 첫 칸인 주소 칸으로 포커스가 되돌아와 떠난 것이 되지 않습니다.)
        compose.onNodeWithTag(serverApiKeyFieldTag).performClick()
        field.assert(hasText("https://elsewhere"))
    }

    @Test
    fun theIntervalCanBeTypedPastTheMinimum() {
        // "3"만 친 순간 저장은 최소값 15로 갑니다. 그 15가 돌아와 칸을 덮으면 "30"을 칠 수 없었습니다.
        showSettings(SettingsSection.NOTIFICATIONS)
        compose.onNodeWithTag(settingsListTag).performScrollToNode(hasTestTag(reminderIntervalFieldTag))

        val field = compose.onNodeWithTag(reminderIntervalFieldTag)
        field.performTextClearance()
        field.performTextInput("3")
        compose.runOnIdle { reminders = sentReminders.last() }
        field.performTextInput("0")
        compose.runOnIdle { reminders = sentReminders.last() }

        field.assert(hasText("30"))
        compose.runOnIdle { assertEquals(30, reminders.intervalMinutes) }

        // 15보다 작게 치는 동안은 친 그대로 두고, 갈래를 나갔다 들어오면 실제로 저장된 값을 보여 줍니다.
        field.performTextClearance()
        field.performTextInput("5")
        compose.runOnIdle { reminders = sentReminders.last() }
        field.assert(hasText("5"))
        compose.runOnIdle { section = null }
        compose.runOnIdle { section = SettingsSection.NOTIFICATIONS }
        compose.onNodeWithTag(settingsListTag).performScrollToNode(hasTestTag(reminderIntervalFieldTag))
        compose.onNodeWithTag(reminderIntervalFieldTag).assert(hasText("15"))
    }

    /** 실제 앱처럼 갈래를 밖에서 들고 있습니다(`AppGoodWordsApp`). */
    private fun showSettings(start: SettingsSection) {
        section = start
        compose.setContent {
            AppGoodWordsTheme {
                SettingsScreen(
                    settings = reminders,
                    serverSyncSettings = sync,
                    syncStatus = SyncStatus(),
                    categories = emptyList(),
                    syncBackups = emptyList(),
                    syncBackupDirectory = "",
                    notificationsBlocked = false,
                    onSettingsChanged = { sentReminders += it },
                    onServerSyncSettingsChanged = { sentSync += it },
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
                    section = section,
                    onSectionChange = { section = it }
                )
            }
        }
    }
}
