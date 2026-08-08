package com.codex.appgoodwords.data

data class ServerSyncSettings(
    val serverUrl: String = "",
    val apiKey: String = "",
    /** 기본값은 꺼짐. 주소를 넣기 전에는 배경에서 돌 이유가 없다. */
    val autoSyncEnabled: Boolean = false,
    val autoSyncIntervalHours: Int = DEFAULT_INTERVAL_HOURS
) {
    val effectiveIntervalHours: Int
        get() = autoSyncIntervalHours.coerceIn(MIN_INTERVAL_HOURS, MAX_INTERVAL_HOURS)

    /** 주소가 없으면 자동 동기화를 걸어 봐야 매번 실패한다. */
    val canAutoSync: Boolean
        get() = autoSyncEnabled && serverUrl.isNotBlank()

    companion object {
        const val DEFAULT_INTERVAL_HOURS = 6
        const val MIN_INTERVAL_HOURS = 1
        const val MAX_INTERVAL_HOURS = 24
        val INTERVAL_CHOICES = listOf(1, 6, 24)
    }
}

/** 마지막 자동/수동 동기화 결과. 배경 동기화는 실패해도 화면에 뜨지 않아 따로 남긴다. */
data class SyncStatus(
    val lastSyncAt: Long = 0L,
    val lastError: String = ""
) {
    val hasSynced: Boolean
        get() = lastSyncAt > 0L

    val failed: Boolean
        get() = lastError.isNotBlank()
}
