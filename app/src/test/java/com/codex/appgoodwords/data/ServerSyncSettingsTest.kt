package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerSyncSettingsTest {
    @Test
    fun autoSync_isOffByDefault() {
        assertFalse(ServerSyncSettings().autoSyncEnabled)
    }

    @Test
    fun autoSync_needsAServerAddress() {
        // 주소 없이 예약해 두면 배경에서 깨어나 매번 실패한다.
        val enabledWithoutUrl = ServerSyncSettings(serverUrl = "", autoSyncEnabled = true)

        assertFalse(enabledWithoutUrl.canAutoSync)
        assertTrue(enabledWithoutUrl.copy(serverUrl = "http://192.168.0.10:8765").canAutoSync)
    }

    @Test
    fun autoSync_offMeansNoScheduleEvenWithAddress() {
        val settings = ServerSyncSettings(serverUrl = "http://192.168.0.10:8765", autoSyncEnabled = false)

        assertFalse(settings.canAutoSync)
    }

    @Test
    fun interval_staysWithinWhatWorkManagerCanSchedule() {
        assertEquals(1, ServerSyncSettings(autoSyncIntervalHours = 0).effectiveIntervalHours)
        assertEquals(1, ServerSyncSettings(autoSyncIntervalHours = -5).effectiveIntervalHours)
        assertEquals(24, ServerSyncSettings(autoSyncIntervalHours = 999).effectiveIntervalHours)
        assertEquals(6, ServerSyncSettings().effectiveIntervalHours)
    }

    @Test
    fun syncStatus_separatesNeverSyncedFromFailed() {
        assertFalse(SyncStatus().hasSynced)
        assertFalse(SyncStatus(lastSyncAt = 1_000L).failed)
        assertTrue(SyncStatus(lastSyncAt = 1_000L, lastError = "연결 실패").failed)
    }
}
