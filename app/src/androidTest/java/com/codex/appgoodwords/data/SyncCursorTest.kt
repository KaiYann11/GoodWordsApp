package com.codex.appgoodwords.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.codex.appgoodwords.AppGoodWordsApplication
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 동기화가 끝나며 적는 "어디까지 보냈나"가, 그 사이에 온 "통째로 보내 달라"를 지우지 않는지 봅니다.
 *
 * 지난날에 적은 체크(2026-11-18)는 시각이 지난 때라 "바뀐 것만" 보내기에 잡히지 않습니다. 동기화가 도는 중에 적으면 이번
 * 스냅샷에도 없을 수 있어, 끝난 동기화가 보낸 시각을 그대로 적으면 그 체크는 영영 서버에 가지 않습니다.
 */
class SyncCursorTest {
    private val settingsStore =
        (ApplicationProvider.getApplicationContext<Context>() as AppGoodWordsApplication).container.settingsStore

    @After
    fun tearDown() = runBlocking { settingsStore.clearSyncCursor() }

    @Test
    fun aFullPushAskedWhileSyncingSurvivesThatSync() = runBlocking {
        settingsStore.updateSyncCursor(
            SyncCursor(serverRev = 5, serverEpoch = 1, lastPushAt = 1_000L, fullPushRequests = settingsStore.getSyncCursor().fullPushRequests)
        )
        val started = settingsStore.getSyncCursor()

        // 동기화가 도는 사이에 지난날 체크를 적었습니다.
        settingsStore.requestFullPush()
        // 동기화는 시작할 때 읽은 번호를 들고 끝납니다.
        settingsStore.updateSyncCursor(
            SyncCursor(serverRev = 9, serverEpoch = 1, lastPushAt = 5_000L, fullPushRequests = started.fullPushRequests)
        )

        val after = settingsStore.getSyncCursor()
        assertEquals(9L, after.serverRev)
        assertEquals("다음 동기화가 통째로 보내야 합니다.", 0L, after.lastPushAt)
    }

    @Test
    fun aRequestMadeBeforeTheSyncIsServedByIt() = runBlocking {
        settingsStore.requestFullPush()
        val started = settingsStore.getSyncCursor()
        assertEquals(0L, started.lastPushAt)

        settingsStore.updateSyncCursor(
            SyncCursor(serverRev = 9, serverEpoch = 1, lastPushAt = 5_000L, fullPushRequests = started.fullPushRequests)
        )

        assertEquals(5_000L, settingsStore.getSyncCursor().lastPushAt)
    }

    @Test
    fun withoutARequestTheSyncKeepsItsMark() = runBlocking {
        val started = settingsStore.getSyncCursor()

        settingsStore.updateSyncCursor(
            SyncCursor(serverRev = 9, serverEpoch = 1, lastPushAt = 5_000L, fullPushRequests = started.fullPushRequests)
        )

        assertEquals(5_000L, settingsStore.getSyncCursor().lastPushAt)
    }
}
