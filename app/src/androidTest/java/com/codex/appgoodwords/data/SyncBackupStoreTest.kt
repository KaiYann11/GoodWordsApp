package com.codex.appgoodwords.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 자동 동기화가 자주 돌아도 사용자가 직접 만든 백업이 밀려나면 안 됩니다.
 * 되돌릴 곳이 사라지는 문제라 종류별로 따로 세는지 확인합니다.
 */
class SyncBackupStoreTest {
    private lateinit var store: SyncBackupStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        store = SyncBackupStore(context)
        runBlocking {
            File(store.directoryPath()).listFiles()?.forEach { it.delete() }
        }
    }

    @Test
    fun automaticBackupsDoNotPushOutManualOnes() = runBlocking {
        store.save(SyncBackupKind.BEFORE_UPLOAD, emptySnapshot())

        repeat(8) {
            store.save(SyncBackupKind.BEFORE_AUTO_MERGE, emptySnapshot())
            // 파일 이름이 초 단위라 같은 초에 몰리면 서로 덮어쓴다.
            Thread.sleep(1_100)
        }

        val backups = store.list()
        assertTrue(
            "직접 만든 백업이 사라졌습니다.",
            backups.any { it.kind == SyncBackupKind.BEFORE_UPLOAD }
        )
        assertEquals(
            "자동 백업은 5개까지만 남아야 합니다.",
            5,
            backups.count { it.kind == SyncBackupKind.BEFORE_AUTO_MERGE }
        )
    }

    @Test
    fun savedBackupCanBeReadBack() = runBlocking {
        val snapshot = emptySnapshot().copy(
            items = listOf(
                ContentItemEntity(
                    syncId = "item-1",
                    type = ContentType.QUOTE,
                    title = "백업에 담긴 글귀",
                    body = "본문",
                    createdAt = 1_000L
                )
            )
        )

        val backup = store.save(SyncBackupKind.BEFORE_MERGE, snapshot)

        assertEquals("백업에 담긴 글귀", store.load(backup).items.single().title)
    }

    private fun emptySnapshot() = AppDataSnapshot(
        items = emptyList(),
        events = emptyList(),
        routines = emptyList(),
        routineChecks = emptyList(),
        routineMemos = emptyList(),
        settings = ReminderSettings()
    )
}
