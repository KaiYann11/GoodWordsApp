package com.codex.appgoodwords.data

/**
 * 첨부 주소를 다루는 규칙.
 *
 * 첨부는 두 가지입니다.
 * - 기기 안 파일: `content://...`. 그 기기에서만 열립니다.
 * - 서버가 보관하는 파일: `appgoodwords://attachment/{sha256}.{확장자}`. 어느 기기에서나 열립니다.
 *
 * 동기화로 오가는 것은 주소 문자열뿐이라, 기기 주소만 들고 있으면 다른 기기와 웹에서는 열 수 없습니다.
 * 그래서 동기화 직전에 기기 파일을 서버로 올리고 주소를 서버 것으로 바꿔 둡니다.
 *
 * 서버의 `attachmentScheme`과 값이 같아야 합니다.
 */
object AttachmentUris {
    const val SCHEME = "appgoodwords://attachment/"

    private val idPattern = Regex("^[a-f0-9]{64}\\.[a-z0-9]{1,5}$")

    fun isServerAttachment(uri: String): Boolean = idOf(uri) != null

    /** 서버 주소가 아니면 null입니다. 모양이 어긋난 값도 null로 봅니다. */
    fun idOf(uri: String): String? {
        if (!uri.startsWith(SCHEME)) return null
        val id = uri.removePrefix(SCHEME)
        return if (idPattern.matches(id)) id else null
    }

    /** 기기에 있는 파일이라 이 기기에서만 열 수 있는 주소인지. */
    fun isLocal(uri: String): Boolean = uri.isNotBlank() && !uri.startsWith(SCHEME)

    /**
     * 실제로 받아올 수 있는 http 주소로 바꿉니다.
     *
     * 서버 주소가 없으면 null입니다. 서버를 안 쓰는 사용자에게는 받아올 곳이 없습니다.
     */
    fun toHttpUrl(serverUrl: String, uri: String): String? {
        if (serverUrl.isBlank()) return null
        val id = idOf(uri) ?: return null
        return "${ServerUrlPolicy.normalize(serverUrl)}/api/attachments/$id"
    }
}
