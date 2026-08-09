package com.codex.appgoodwords.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.codex.appgoodwords.AppGoodWordsApplication
import com.codex.appgoodwords.data.AppDataSnapshot
import com.codex.appgoodwords.data.ReminderSettings
import com.codex.appgoodwords.data.SyncBackupKind
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 배경 동기화는 화면 밖에서 데이터를 바꿉니다.
 *
 * 항목 목록은 Room이 흘려 주지만 백업 목록은 ViewModel이 한 번 읽고 들고 있어서,
 * 다시 읽지 않으면 자동 동기화가 만든 백업이 설정 화면에 나타나지 않습니다.
 * 그러면 사용자는 되돌릴 수 있는 지점이 있는데도 없다고 믿습니다.
 */
class SyncStatusRefreshTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = (context as AppGoodWordsApplication).container

    @After
    fun tearDown() = runBlocking {
        container.settingsStore.recordSyncResult(0L, error = "")
        deleteTestBackups()
    }

    @Test
    fun backgroundSyncResultRefreshesTheBackupList() = runBlocking {
        val viewModel = withContext(Dispatchers.Main) { MainViewModel(container) }
        // 시작할 때의 첫 읽기가 끝난 뒤부터 세야 합니다.
        val before = withTimeout(TIMEOUT_MS) {
            viewModel.syncBackupDirectory.first { it.isNotBlank() }
            viewModel.syncBackups.value
        }

        // SyncWorker가 하는 것과 같은 순서: 백업을 남기고 결과를 기록한다.
        container.syncBackupStore.save(SyncBackupKind.BEFORE_AUTO_MERGE, emptySnapshot())
        container.settingsStore.recordSyncResult(System.currentTimeMillis(), error = "")

        val after = withTimeout(TIMEOUT_MS) {
            viewModel.syncBackups.first { backups -> backups.size > before.size }
        }

        assertTrue(
            "자동 동기화가 남긴 백업이 목록에 없습니다.",
            after.any { it.kind == SyncBackupKind.BEFORE_AUTO_MERGE }
        )
    }

    /** 이 테스트가 보는 것은 목록이 다시 읽히는지뿐이라, 내용은 비어 있어도 됩니다. */
    private fun emptySnapshot() = AppDataSnapshot(
        items = emptyList(),
        events = emptyList(),
        routines = emptyList(),
        routineChecks = emptyList(),
        routineMemos = emptyList(),
        settings = ReminderSettings()
    )

    private fun deleteTestBackups() {
        val directory = context.getExternalFilesDir("sync-backups") ?: return
        directory.listFiles { file: File ->
            file.name.startsWith("sync-${SyncBackupKind.BEFORE_AUTO_MERGE.fileNamePrefix}-")
        }?.forEach { it.delete() }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
