package com.codex.appgoodwords.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.codex.appgoodwords.data.ReminderSettings
import com.codex.appgoodwords.data.ServerSyncSettings
import com.codex.appgoodwords.data.SyncBackup
import com.codex.appgoodwords.data.SyncBackupKind
import com.codex.appgoodwords.data.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 동기화 백업은 종류마다 5개씩 쌓여 많으면 25개입니다. 설정에는 최근 3개만 두고 나머지는 "모두 보기"에서
 * 봅니다(사용자가 정함, 2026-11-08). 지운 백업은 되살릴 수 없어 한 번 더 묻는지도 봅니다.
 */
class SyncBackupListTest {
    @get:Rule
    val compose = createComposeRule()

    // 최근 것부터입니다([com.codex.appgoodwords.data.SyncBackupStore.list]와 같게).
    private val backups = listOf(
        backup("sync-before-merge-20261108-082200.json", SyncBackupKind.BEFORE_MERGE, 5_000L),
        backup("sync-before-auto-merge-20261107-082200.json", SyncBackupKind.BEFORE_AUTO_MERGE, 4_000L),
        backup("sync-before-merge-20261102-082200.json", SyncBackupKind.BEFORE_MERGE, 3_000L),
        backup("sync-before-upload-20261029-081400.json", SyncBackupKind.BEFORE_UPLOAD, 2_000L),
        backup("sync-before-auto-merge-20261028-081400.json", SyncBackupKind.BEFORE_AUTO_MERGE, 1_000L)
    )

    @Test
    fun settingsShowsTheLatestThreeAndOpensTheRest() {
        var opened = false
        compose.setContent { settingsScreen(backups, onOpenAllBackups = { opened = true }) }

        scrollTo(hasTestTag(settingsAllBackupsTag))

        compose.onNodeWithText("백업 모두 보기 (5개)").assertIsDisplayed()
        backups.take(3).forEach { compose.onNodeWithTag(backupRestoreTag(it.fileName)).assertExists() }
        backups.drop(3).forEach { compose.onNodeWithTag(backupRestoreTag(it.fileName)).assertDoesNotExist() }

        compose.onNodeWithTag(settingsAllBackupsTag).performClick()
        assertTrue(opened)
    }

    @Test
    fun fewBackupsNeedNoSecondScreen() {
        compose.setContent { settingsScreen(backups.take(2)) }

        scrollTo(hasTestTag(backupRestoreTag(backups[1].fileName)))

        compose.onNodeWithTag(settingsAllBackupsTag).assertDoesNotExist()
    }

    @Test
    fun deletingAsksFirst() {
        var deleted: SyncBackup? = null
        compose.setContent { settingsScreen(backups, onDeleteBackup = { deleted = it }) }
        val target = backups[0]

        scrollTo(hasTestTag(backupDeleteTag(target.fileName)))
        compose.onNodeWithTag(backupDeleteTag(target.fileName)).performClick()
        compose.onNodeWithText("되살릴 수 없습니다", substring = true).assertIsDisplayed()
        compose.onNode(hasText("취소") and hasAnyAncestor(isDialog())).performClick()
        assertNull("취소했는데 지웠습니다.", deleted)

        compose.onNodeWithTag(backupDeleteTag(target.fileName)).performClick()
        compose.onNodeWithTag(backupConfirmTag).performClick()
        assertEquals(target, deleted)
    }

    @Test
    fun restoringStillAsksFirst() {
        var restored: SyncBackup? = null
        compose.setContent { settingsScreen(backups, onRestoreBackup = { restored = it }) }
        val target = backups[1]

        scrollTo(hasTestTag(backupRestoreTag(target.fileName)))
        compose.onNodeWithTag(backupRestoreTag(target.fileName)).performClick()
        compose.onNodeWithText("백업을 복원할까요?").assertIsDisplayed()
        compose.onNodeWithTag(backupConfirmTag).performClick()

        assertEquals(target, restored)
    }

    @Test
    fun allBackupsAreGroupedByKindWithTheNewestKindFirst() {
        var deleted: SyncBackup? = null
        compose.setContent {
            SyncBackupsScreen(
                backups = backups,
                directory = "",
                onRestoreBackup = {},
                onDeleteBackup = { deleted = it }
            )
        }

        val merge = compose.onNodeWithText("병합 전 기기 데이터 2개").fetchSemanticsNode().boundsInRoot.top
        val autoMerge = compose.onNodeWithText("자동 동기화 전 기기 데이터 2개").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag(syncBackupsListTag).performScrollToNode(hasText("업로드 전 서버 데이터 1개"))
        compose.onNodeWithText("업로드 전 서버 데이터 1개").assertIsDisplayed()
        assertTrue("가장 최근 백업의 종류가 맨 위여야 합니다.", merge < autoMerge)

        val target = backups[3]
        compose.onNodeWithTag(syncBackupsListTag).performScrollToNode(hasTestTag(backupDeleteTag(target.fileName)))
        compose.onNodeWithTag(backupDeleteTag(target.fileName)).performClick()
        compose.onNodeWithTag(backupConfirmTag).performClick()
        assertEquals(target, deleted)
    }

    private fun backup(fileName: String, kind: SyncBackupKind, createdAt: Long) =
        SyncBackup(fileName = fileName, kind = kind, createdAt = createdAt, sizeBytes = 3_700_000L)

    private fun scrollTo(matcher: SemanticsMatcher) {
        compose.onNode(
            hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        ).performScrollToNode(matcher)
    }

    @Composable
    private fun settingsScreen(
        syncBackups: List<SyncBackup>,
        onRestoreBackup: (SyncBackup) -> Unit = {},
        onDeleteBackup: (SyncBackup) -> Unit = {},
        onOpenAllBackups: () -> Unit = {}
    ) {
        SettingsScreen(
            settings = ReminderSettings(),
            serverSyncSettings = ServerSyncSettings(),
            syncStatus = SyncStatus(),
            categories = emptyList(),
            syncBackups = syncBackups,
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
            onRestoreBackup = onRestoreBackup,
            onDeleteBackup = onDeleteBackup,
            onOpenAllBackups = onOpenAllBackups,
            onDeleteCategory = {},
            // 백업 카드는 서버·동기화 갈래 안에 있습니다.
            section = SettingsSection.SYNC
        )
    }
}
