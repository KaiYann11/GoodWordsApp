package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 첨부 주소를 가르는 규칙입니다.
 *
 * 기기 주소와 서버 주소를 잘못 가르면, 기기 파일을 서버에서 받으려 하거나
 * 이미 올린 파일을 동기화마다 다시 올립니다.
 */
class AttachmentUrisTest {
    private val id = "a".repeat(64) + ".jpg"
    private val serverUri = "${AttachmentUris.SCHEME}$id"

    @Test
    fun aServerUriIsRecognised() {
        assertTrue(AttachmentUris.isServerAttachment(serverUri))
        assertEquals(id, AttachmentUris.idOf(serverUri))
        assertFalse(AttachmentUris.isLocal(serverUri))
    }

    @Test
    fun aDeviceUriIsLocal() {
        val deviceUri = "content://com.android.providers.media.documents/document/image%3A1000"

        assertTrue(AttachmentUris.isLocal(deviceUri))
        assertFalse(AttachmentUris.isServerAttachment(deviceUri))
        assertNull(AttachmentUris.idOf(deviceUri))
    }

    @Test
    fun aBrokenServerUriIsNotAccepted() {
        // 모양이 어긋난 값을 그대로 주소에 붙이면 서버에서 남의 파일을 집을 수 있습니다.
        assertNull(AttachmentUris.idOf("${AttachmentUris.SCHEME}../../db.json"))
        assertNull(AttachmentUris.idOf("${AttachmentUris.SCHEME}not-a-hash.jpg"))
        assertNull(AttachmentUris.idOf("${AttachmentUris.SCHEME}${"a".repeat(64)}"))
        assertNull(AttachmentUris.idOf("${AttachmentUris.SCHEME}${"A".repeat(64)}.jpg"))
    }

    @Test
    fun aServerUriBecomesAnHttpAddress() {
        assertEquals(
            "http://10.0.2.2:8765/api/attachments/$id",
            AttachmentUris.toHttpUrl("http://10.0.2.2:8765", serverUri)
        )
    }

    @Test
    fun withoutAServerThereIsNowhereToFetchFrom() {
        assertNull(AttachmentUris.toHttpUrl("", serverUri))
    }

    @Test
    fun aDeviceUriIsNeverFetchedFromTheServer() {
        // 기기 파일을 서버에서 받으려 하면 404만 돌아옵니다.
        assertNull(AttachmentUris.toHttpUrl("http://10.0.2.2:8765", "content://photo/1"))
    }

    @Test
    fun anEmptyUriIsNeitherKind() {
        assertFalse(AttachmentUris.isLocal(""))
        assertFalse(AttachmentUris.isServerAttachment(""))
    }
}
