package com.codex.appgoodwords.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.codex.appgoodwords.data.ReminderSettings
import com.codex.appgoodwords.data.ServerSyncSettings
import com.codex.appgoodwords.data.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 자동 동기화는 배경에서 돌아서 결과가 화면에 뜨지 않습니다.
 * 설정 화면이 상태를 제대로 보여 주지 못하면 사용자는 안 되는 걸 되는 줄 압니다.
 *
 * 설정 화면은 LazyColumn이라 보이지 않는 항목은 아예 만들어지지도 않습니다.
 * 그래서 확인하기 전에 목록을 그 자리까지 굴려야 합니다.
 */
class AutoSyncSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    private val serverUrl = "http://192.168.0.10:8765"

    @Test
    fun intervalChoicesAppearOnlyWhenAutoSyncIsOn() {
        compose.setContent {
            settingsScreen(ServerSyncSettings(serverUrl = serverUrl, autoSyncEnabled = false))
        }

        scrollTo(hasTestTag(autoSyncSwitchTag))

        compose.onNodeWithText("6시간").assertDoesNotExist()
    }

    @Test
    fun turningAutoSyncOnShowsTheInterval() {
        compose.setContent {
            settingsScreen(
                ServerSyncSettings(serverUrl = serverUrl, autoSyncEnabled = true, autoSyncIntervalHours = 6)
            )
        }

        // 마지막 칩까지 굴려야 줄 전체가 화면에 들어온다.
        scrollTo(hasText("24시간"))

        compose.onNodeWithText("1시간").assertIsDisplayed()
        compose.onNodeWithText("6시간").assertIsDisplayed()
        compose.onNodeWithText("24시간").assertIsDisplayed()
    }

    @Test
    fun pickingAnIntervalKeepsTheOtherSettings() {
        var reported: ServerSyncSettings? = null
        compose.setContent {
            settingsScreen(
                serverSyncSettings = ServerSyncSettings(
                    serverUrl = serverUrl,
                    autoSyncEnabled = true,
                    autoSyncIntervalHours = 6
                ),
                onServerSyncSettingsChanged = { reported = it }
            )
        }

        scrollTo(hasText("24시간"))
        compose.onNodeWithText("24시간").performClick()

        assertEquals(24, reported?.autoSyncIntervalHours)
        assertEquals("켜짐 상태를 잃으면 안 됩니다.", true, reported?.autoSyncEnabled)
        assertEquals("주소를 잃으면 안 됩니다.", serverUrl, reported?.serverUrl)
    }

    @Test
    fun autoSyncCannotBeTurnedOnWithoutAnAddress() {
        compose.setContent { settingsScreen(ServerSyncSettings(serverUrl = "")) }

        scrollTo(hasTestTag(autoSyncSwitchTag))

        compose.onNodeWithTag(autoSyncSwitchTag).assertIsNotEnabled()
        compose.onNodeWithText("서버 주소를 먼저 넣어야 켤 수 있습니다.").assertIsDisplayed()
    }

    @Test
    fun autoSyncCanBeTurnedOnOnceAnAddressIsSet() {
        var reported: ServerSyncSettings? = null
        compose.setContent {
            settingsScreen(
                serverSyncSettings = ServerSyncSettings(serverUrl = serverUrl),
                onServerSyncSettingsChanged = { reported = it }
            )
        }

        scrollTo(hasTestTag(autoSyncSwitchTag))
        compose.onNodeWithTag(autoSyncSwitchTag).performClick()

        assertEquals(true, reported?.autoSyncEnabled)
    }

    @Test
    fun failedBackgroundSyncIsVisible() {
        compose.setContent {
            settingsScreen(
                serverSyncSettings = ServerSyncSettings(serverUrl = serverUrl),
                syncStatus = SyncStatus(lastSyncAt = 1_700_000_000_000L, lastError = "서버에 연결할 수 없습니다.")
            )
        }

        scrollTo(hasText("서버에 연결할 수 없습니다.", substring = true))

        compose.onNodeWithText("서버에 연결할 수 없습니다.", substring = true).assertIsDisplayed()
    }

    @Test
    fun neverSyncedIsStatedPlainly() {
        compose.setContent { settingsScreen(ServerSyncSettings()) }

        scrollTo(hasText("마지막 동기화: 없음"))

        compose.onNodeWithText("마지막 동기화: 없음").assertIsDisplayed()
    }

    /** 화면에는 가로로 굴러가는 칩 목록도 있어서, 세로 목록만 골라야 한다. */
    private fun scrollTo(matcher: SemanticsMatcher) {
        compose.onNode(
            hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        ).performScrollToNode(matcher)
    }

    @Composable
    private fun settingsScreen(
        serverSyncSettings: ServerSyncSettings,
        syncStatus: SyncStatus = SyncStatus(),
        onServerSyncSettingsChanged: (ServerSyncSettings) -> Unit = {}
    ) {
        SettingsScreen(
            settings = ReminderSettings(),
            serverSyncSettings = serverSyncSettings,
            syncStatus = syncStatus,
            categories = emptyList(),
            syncBackups = emptyList(),
            syncBackupDirectory = "",
            onSettingsChanged = {},
            onServerSyncSettingsChanged = onServerSyncSettingsChanged,
            onSendTestNotification = {},
            onResetViewCounts = {},
            onExportRequested = {},
            onImportRequested = {},
            onTestServerConnection = {},
            onSyncWithServer = {},
            onUploadToServer = {},
            onDownloadFromServer = {},
            onRestoreBackup = {},
            onDeleteCategory = {}
        )
    }
}
