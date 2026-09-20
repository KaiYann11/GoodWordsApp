package com.codex.appgoodwords.data

import java.net.URI

/** 서버 접속 키와 개인 기록은 인증서가 검증되는 HTTPS로만 보냅니다. */
object ServerUrlPolicy {
    fun normalize(serverUrl: String): String {
        val normalized = serverUrl.trim().trimEnd('/')
        require(normalized.isNotBlank()) { "서버 주소를 입력해 주세요." }
        val uri = runCatching { URI(normalized) }.getOrNull()
        require(uri != null && uri.scheme == "https") {
            "기록과 접속 키를 보호하기 위해 https:// 서버 주소가 필요합니다. 기존 http:// 주소를 HTTPS 주소로 바꿔 주세요."
        }
        require(!uri.host.isNullOrBlank()) { "서버 주소에서 호스트를 읽을 수 없습니다." }
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
            "서버 주소에는 로그인 정보, 쿼리, #을 넣을 수 없습니다."
        }
        require(uri.port == -1 || uri.port in 1..65535) { "서버 포트가 올바르지 않습니다." }
        return normalized
    }
}
