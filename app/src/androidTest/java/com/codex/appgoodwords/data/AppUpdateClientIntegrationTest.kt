package com.codex.appgoodwords.data

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * 앱이 서버에서 설치 파일 정보를 실제로 받아 오는지 봅니다.
 *
 * 형식 자체는 `AppUpdateTest`가 덮고, 여기서는 **서버가 내주는 자리와 앱이 찾는 자리가 같은지**를
 * 봅니다. 게시 스크립트가 `/update/latest.json`에 쓰고 앱이 거기서 읽으므로, 한쪽만 바꾸면
 * 업데이트가 영영 안 보입니다. 접속 키 없이도 받아지는지도 함께 봅니다.
 *
 * 서버가 지정되지 않으면 건너뜁니다. 운영 서버를 쓰지 마세요.
 */
class AppUpdateClientIntegrationTest {
    private val client = AppUpdateClient()

    private val serverUrl: String =
        InstrumentationRegistry.getArguments().getString("testServerUrl").orEmpty()

    @Before
    fun requireServer() {
        assumeTrue("testServerUrl에 별도 HTTPS 테스트 서버를 지정해 주세요.", serverUrl.isNotBlank())
    }

    @Test
    fun theServerServesTheReleaseInfoWhereTheAppLooksForIt() = runBlocking {
        val info = client.fetchLatest(serverUrl)

        assertTrue("버전 번호가 있어야 새 버전인지 가릴 수 있습니다.", info.versionCode > 0)
        assertTrue(info.fileName.endsWith(".apk"))
        assertTrue("해시가 있어야 받은 파일을 믿을 수 있습니다.", info.sha256.length == 64)
        assertEquals("/update/${info.fileName}", info.path)
    }

    @Test
    fun theApkComesDownWholeAndMatchesItsHash() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = client.fetchLatest(serverUrl)
        val target = File(context.cacheDir, "update-test/${info.fileName}")

        var lastProgress = 0L
        val file = client.download(serverUrl, info, target) { received -> lastProgress = received }

        try {
            assertTrue(file.exists())
            // 받는 동안 얼마나 왔는지 알려야 25MB를 기다리는 사람이 멈춘 줄 알지 않습니다.
            assertEquals(info.sizeBytes, lastProgress)
            assertEquals(info.sizeBytes, file.length())
        } finally {
            file.delete()
        }
    }

    @Test
    fun aFileThatDoesNotMatchItsHashIsThrownAway() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val real = client.fetchLatest(serverUrl)
        // 서버가 준 파일은 그대로인데 기대하는 해시만 다른 경우입니다 — 바꿔치기와 같은 모양입니다.
        val tampered = real.copy(sha256 = "b".repeat(64))
        val target = File(context.cacheDir, "update-test/${tampered.fileName}")

        val failure = runCatching { client.download(serverUrl, tampered, target) }.exceptionOrNull()

        assertNotNull("해시가 달라도 그냥 넘어갔습니다.", failure)
        assertTrue(failure!!.message.orEmpty().contains("다릅니다"))
        // 못 믿을 파일을 남겨 두면 다음에 그것이 설치됩니다.
        assertFalse("못 믿을 파일이 남아 있습니다.", target.exists())
    }

    @Test
    fun plainHttpIsRefusedBeforeAnythingLeavesTheDevice() = runBlocking {
        val failure = runCatching {
            client.fetchLatest(serverUrl.replace("https://", "http://"))
        }.exceptionOrNull()

        assertNotNull("평문 주소로도 설치 파일을 받으려 했습니다.", failure)
        assertTrue(failure!!.message.orEmpty().contains("https://"))
    }
}
