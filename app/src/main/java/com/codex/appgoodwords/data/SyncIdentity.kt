package com.codex.appgoodwords.data

import java.util.UUID

/**
 * 기기 간 병합을 위한 레코드 식별자.
 *
 * Room의 기본 키는 기기마다 따로 증가하는 Long이라 A기기의 id=5와 B기기의 id=5가 서로 다른 항목입니다.
 * 기본 키를 UUID로 바꾸면 알림 extras, 위젯 인텐트, 화면 이동까지 전부 영향을 받으므로
 * 기본 키는 그대로 두고 [syncId]를 따로 부여해 병합할 때만 씁니다.
 */
object SyncIdentity {
    fun newId(): String = UUID.randomUUID().toString()

    /** 예전 데이터나 구버전 서버에서 온 레코드는 syncId가 비어 있을 수 있다. */
    fun orNew(syncId: String?): String = syncId?.trim().takeUnless { it.isNullOrBlank() } ?: newId()
}

enum class SyncEntityType {
    CONTENT_ITEM,
    EXPOSURE_EVENT,
    ROUTINE,
    ROUTINE_CHECK,
    ROUTINE_MEMO;

    companion object {
        fun fromNameOrNull(value: String?): SyncEntityType? =
            values().firstOrNull { it.name == value?.trim()?.uppercase() }
    }
}
