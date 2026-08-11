package com.codex.appgoodwords.data

/**
 * 앱과 서버가 같은 동기화 포맷을 쓰는지 확인합니다.
 *
 * 버전이 다르면 상대가 모르는 항목을 조용히 떨어뜨립니다.
 * 예를 들어 9 이전 서버는 자식이 부모를 가리키는 syncId를 몰라서, 병합 결과를 받아 저장하면
 * 이력이 부모를 잃고 메모는 버려집니다. 오류 없이 벌어지므로 아예 막습니다.
 */
object SyncSchema {
    /** 맞으면 null, 다르면 사용자에게 보일 사유. */
    fun mismatchMessage(
        serverVersion: Int,
        appVersion: Int = AppDataJson.schemaVersion
    ): String? {
        if (serverVersion == appVersion) return null

        val whichIsOlder = if (serverVersion < appVersion) {
            "서버를 최신 버전으로 올려 주세요."
        } else {
            "앱을 최신 버전으로 올려 주세요."
        }
        return "서버와 앱의 동기화 형식이 다릅니다" +
            "(서버 $serverVersion, 앱 $appVersion). $whichIsOlder"
    }
}
