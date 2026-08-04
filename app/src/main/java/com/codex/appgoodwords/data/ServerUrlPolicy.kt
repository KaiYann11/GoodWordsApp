package com.codex.appgoodwords.data

import java.net.URI

/**
 * 동기화 서버 주소 검증.
 *
 * 업로드는 개인 기록 전체(글귀, 이력, 루틴, 메모)를 한 번에 보냅니다.
 * 주소를 잘못 넣어 그 스냅샷이 평문으로 외부 호스트에 나가는 일을 막기 위해,
 * 평문 http는 같은 네트워크 안의 사설/루프백 주소로만 허용합니다.
 *
 * network security config는 CIDR 대역을 표현할 수 없어 이 규칙을 XML로 쓸 수 없으므로
 * 여기에서 강제합니다.
 */
object ServerUrlPolicy {
    fun normalize(serverUrl: String): String {
        val normalized = serverUrl.trim().trimEnd('/')
        require(normalized.isNotBlank()) { "서버 주소를 입력해 주세요." }
        require(normalized.startsWith("http://") || normalized.startsWith("https://")) {
            "서버 주소는 http:// 또는 https://로 시작해야 합니다."
        }

        val host = hostOf(normalized)
        require(!host.isNullOrBlank()) { "서버 주소에서 호스트를 읽을 수 없습니다: $serverUrl" }

        if (normalized.startsWith("http://") && !isLocalNetworkHost(host)) {
            throw IllegalArgumentException(
                "외부 주소($host)로는 평문 http 동기화를 보낼 수 없습니다. " +
                    "같은 네트워크의 사설 IP를 쓰거나 https:// 주소를 사용해 주세요."
            )
        }

        return normalized
    }

    /** 같은 네트워크 안이라고 볼 수 있는 주소인지. */
    fun isLocalNetworkHost(host: String): Boolean {
        val candidate = host.trim().lowercase().removeSurrounding("[", "]")
        if (candidate == "localhost" || candidate == "::1") return true
        // mDNS 이름(예: mac-mini.local)도 같은 네트워크로 본다.
        if (candidate.endsWith(".local")) return true

        val octets = candidate.split(".")
        if (octets.size != 4) return false
        val values = octets.map { octet ->
            val value = octet.toIntOrNull() ?: return false
            if (value !in 0..255) return false
            value
        }

        return when {
            values[0] == 127 -> true
            values[0] == 10 -> true
            values[0] == 192 && values[1] == 168 -> true
            values[0] == 172 && values[1] in 16..31 -> true
            // 링크 로컬(APIPA). 에뮬레이터의 10.0.2.2는 위 10.x 규칙에 포함된다.
            values[0] == 169 && values[1] == 254 -> true
            else -> false
        }
    }

    private fun hostOf(url: String): String? = runCatching { URI(url).host }.getOrNull()
}
