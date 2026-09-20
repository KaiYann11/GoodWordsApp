package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerUrlPolicyTest {
    @Test
    fun rejectsPlainHttpEvenOnPrivateAndLoopbackAddresses() {
        listOf(
            "http://192.168.0.10:8765",
            "http://10.0.2.2:8765",
            "http://172.16.0.1:8765",
            "http://172.31.255.254:8765",
            "http://127.0.0.1:8765",
            "http://localhost:8765",
            "http://169.254.10.10:8765",
            "http://mac-mini.local:8765"
        ).forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { ServerUrlPolicy.normalize(url) }
        }
    }

    @Test
    fun rejectsPlainHttpToPublicHosts() {
        listOf(
            "http://example.com:8765",
            "http://8.8.8.8:8765",
            "http://172.32.0.1:8765",
            "http://172.15.0.1:8765",
            "http://193.168.0.10:8765"
        ).forEach { url ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                ServerUrlPolicy.normalize(url)
            }
            assertTrue(
                "안내 문구에 https 대안이 있어야 합니다: ${error.message}",
                error.message.orEmpty().contains("https")
            )
        }
    }

    @Test
    fun allowsHttpsAnywhere() {
        val url = "https://sync.example.com"

        assertEquals(url, ServerUrlPolicy.normalize(url))
    }

    @Test
    fun trimsWhitespaceAndTrailingSlash() {
        assertEquals("https://sync.example.com", ServerUrlPolicy.normalize("  https://sync.example.com/  "))
    }

    @Test
    fun rejectsBlankAndSchemelessInput() {
        assertThrows(IllegalArgumentException::class.java) { ServerUrlPolicy.normalize("") }
        assertThrows(IllegalArgumentException::class.java) { ServerUrlPolicy.normalize("   ") }
        assertThrows(IllegalArgumentException::class.java) { ServerUrlPolicy.normalize("192.168.0.10:8765") }
        assertThrows(IllegalArgumentException::class.java) { ServerUrlPolicy.normalize("ftp://192.168.0.10") }
    }

    @Test
    fun rejectsUrlWithoutReadableHost() {
        assertThrows(IllegalArgumentException::class.java) { ServerUrlPolicy.normalize("http://") }
    }

    @Test
    fun rejectsEmbeddedCredentialsAndAmbiguousEndpoints() {
        listOf(
            "https://user:password@sync.example.com", "https://sync.example.com?target=other",
            "https://sync.example.com#other", "https://sync.example.com:70000", "https://"
        ).forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { ServerUrlPolicy.normalize(url) }
        }
    }
}
