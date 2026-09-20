package com.codex.appgoodwords.ui.screen

import androidx.test.platform.app.InstrumentationRegistry
import coil.request.ErrorResult
import com.codex.appgoodwords.AppGoodWordsApplication
import com.codex.appgoodwords.data.ServerSyncClient
import com.codex.appgoodwords.data.ServerSyncSettings
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** 인증서를 정상 검증하는 별도 HTTPS 서버에서 리다이렉트 유출을 검사합니다. */
class HttpsTransportIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val settings = ServerSyncSettings(
        InstrumentationRegistry.getArguments().getString("testServerUrl").orEmpty(),
        InstrumentationRegistry.getArguments().getString("testServerApiKey").orEmpty()
    )

    @Before fun requireFixture() {
        assumeTrue("별도 HTTPS 테스트 서버가 필요합니다.", settings.serverUrl.isNotBlank())
        assertTrue(status().getBoolean("testFixture"))
    }

    @Test fun authenticatedImagesDoNotFollowRedirects() = runBlocking {
        val before = status().getInt("redirectRequests")
        val request = attachmentImageRequest(context, "appgoodwords://attachment/${"b".repeat(64)}.png", settings.serverUrl, settings.apiKey)!!
        val loader = (context.applicationContext as AppGoodWordsApplication).container.attachmentImageLoader
        assertTrue(loader.execute(request) is ErrorResult)
        val after = status()
        assertEquals(before + 1, after.getInt("redirectRequests"))
        assertEquals(0, after.getInt("unexpectedRequests"))
        assertFalse(after.getBoolean("unexpectedKeyReceived"))
    }

    @Test fun syncDoesNotForwardItsKeyToRedirectTargets() = runBlocking {
        val before = status().getInt("redirectRequests")
        val result = runCatching { ServerSyncClient().testConnection(settings.copy(serverUrl = settings.serverUrl + "/redirect")) }
        assertTrue(result.isFailure)
        val after = status()
        assertEquals(before + 1, after.getInt("redirectRequests"))
        assertEquals(0, after.getInt("unexpectedRequests"))
        assertFalse(after.getBoolean("unexpectedKeyReceived"))
    }

    private fun status(): JSONObject {
        val connection = URL(settings.serverUrl + "/probe-status").openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.instanceFollowRedirects = false
        return try { JSONObject(connection.inputStream.bufferedReader().use { it.readText() }) }
        finally { connection.disconnect() }
    }
}
