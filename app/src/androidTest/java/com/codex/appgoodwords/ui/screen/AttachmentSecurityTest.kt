package com.codex.appgoodwords.ui.screen

import android.security.NetworkSecurityPolicy
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import com.codex.appgoodwords.data.ServerSyncClient
import com.codex.appgoodwords.data.ServerSyncSettings
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

class AttachmentSecurityTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fakeKey = "synthetic-review-key"
    private val serverUrl = "https://sync.example.com"

    @Test fun importedExternalImagesCannotCarryTheServerKey() {
        listOf("https://unrelated.example/photo.png", "file:///data/user/0/com.codex.appgoodwords/files/private.json", "data:image/png;base64,AAAA").forEach {
            assertNull(attachmentImageRequest(context, it, serverUrl, fakeKey))
        }
    }

    @Test fun contentProviderRequestsHaveNoServerCredentials() {
        val request = attachmentImageRequest(context, "content://media/external/images/media/1", serverUrl, fakeKey)
        assertNotNull(request)
        assertNull(request!!.headers["X-API-Key"])
    }

    @Test fun onlyValidatedServerAttachmentsReceiveCredentials() {
        val id = "a".repeat(64) + ".png"
        val request = attachmentImageRequest(context, "appgoodwords://attachment/$id", serverUrl, fakeKey)
        assertEquals("$serverUrl/api/attachments/$id", request!!.data)
        assertEquals(fakeKey, request.headers["X-API-Key"])
        assertNull(attachmentImageRequest(context, "appgoodwords://attachment/../../elsewhere", serverUrl, fakeKey))
    }

    @Test fun externalImageDoesNotMakeANetworkRequest() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { listener ->
            listener.soTimeout = 800
            compose.setContent {
                AttachmentViewerDialog(listOf("http://127.0.0.1:${listener.localPort}/probe.png"), 0, {}, serverUrl, fakeKey)
            }
            compose.onNodeWithTag(attachmentViewerPageTag, useUnmergedTree = true).assertExists()
            assertThrows(SocketTimeoutException::class.java) { listener.accept().use {} }
        }
    }

    @Test fun plaintextSyncStopsBeforeOpeningTheConnection() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { listener ->
            listener.soTimeout = 800
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { ServerSyncClient().testConnection(ServerSyncSettings("http://127.0.0.1:${listener.localPort}", fakeKey)) }
            }
            assertThrows(SocketTimeoutException::class.java) { listener.accept().use {} }
        }
        assertFalse(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted)
    }
}
