package com.codex.appgoodwords.data

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 형식이 다른 서버와 주고받으면 서로가 모르는 항목을 조용히 떨어뜨립니다.
 * 오류가 나지 않으므로, 막지 않으면 사용자는 며칠 뒤에야 이력이 비어 있는 걸 알게 됩니다.
 */
class SyncSchemaTest {
    @Test
    fun sameVersionPasses() {
        assertNull(SyncSchema.mismatchMessage(serverVersion = 9, appVersion = 9))
    }

    @Test
    fun anOlderServerIsBlocked() {
        val message = SyncSchema.mismatchMessage(serverVersion = 8, appVersion = 9)

        assertNotNull(message)
        assertTrue("어느 쪽을 올려야 하는지 알려 줘야 합니다: $message", message!!.contains("서버를"))
    }

    @Test
    fun aNewerServerIsAlsoBlocked() {
        // 서버가 앞서도 위험합니다. 앱이 모르는 항목을 지운 채로 돌려주기 때문입니다.
        val message = SyncSchema.mismatchMessage(serverVersion = 10, appVersion = 9)

        assertNotNull(message)
        assertTrue("어느 쪽을 올려야 하는지 알려 줘야 합니다: $message", message!!.contains("앱을"))
    }

    @Test
    fun anUnreachableVersionIsBlocked() {
        // schemaVersion을 못 읽으면 0이 됩니다. 앱 서버가 아닐 수 있으니 진행하면 안 됩니다.
        assertNotNull(SyncSchema.mismatchMessage(serverVersion = 0, appVersion = 9))
    }

    @Test
    fun theCurrentAppVersionIsTheDefault() {
        assertNull(SyncSchema.mismatchMessage(serverVersion = AppDataJson.schemaVersion))
    }
}
