package com.codex.appgoodwords.data

import android.app.DownloadManager
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 서버가 알려 주는 설치 파일 정보.
 *
 * 여기서 거르는 것은 **받아도 되는 파일인지**입니다. 이름을 그대로 주소에 붙여 받으므로,
 * 거르지 않으면 서버(또는 서버인 척하는 쪽)가 가리키는 아무 파일이나 내려받아 설치 화면까지
 * 올라갑니다. 그래서 틀린 값은 조용히 넘기지 않고 전부 막습니다.
 */
class AppUpdateTest {
    private val sha = "19adb1691b3bac1c4b5ce1b6bb43fbd8e7825f91325f0ff0285d9907f8b2a123"

    private fun json(
        fileName: String = "app-good-words-1.6.0-9-19adb169.apk",
        versionName: String = "1.6.0",
        versionCode: Int = 9,
        sizeBytes: Long = 25_010_894,
        sha256: String = sha
    ) = """
        {
          "versionName": "$versionName",
          "versionCode": $versionCode,
          "publishedAt": "2026-09-26T04:02:54.9545091+00:00",
          "fileName": "$fileName",
          "sizeBytes": $sizeBytes,
          "sha256": "$sha256"
        }
    """.trimIndent()

    @Test fun aPublishedReleaseIsReadWholeAndTurnedIntoAPath() {
        val info = AppUpdateJson.parse(json())

        assertEquals("1.6.0", info.versionName)
        assertEquals(9, info.versionCode)
        assertEquals("app-good-words-1.6.0-9-19adb169.apk", info.fileName)
        assertEquals(25_010_894, info.sizeBytes)
        assertEquals(sha, info.sha256)
        // 받아 올 자리는 우리가 짭니다. 서버가 주소를 통째로 주면 어디로든 보낼 수 있습니다.
        assertEquals("/update/app-good-words-1.6.0-9-19adb169.apk", info.path)
        assertEquals("23.9 MB", info.sizeText)
    }

    @Test fun aFileNameThatCouldLeaveTheUpdateFolderIsRefused() {
        val escapes = listOf(
            "../../etc/passwd",
            "../app-good-words-1.6.0-9.apk",
            "app-good-words-1.6.0/../../../other.apk",
            "https://example.com/other.apk",
            "app-good-words-1.6.0-9.apk?x=1",
            "other-app-1.0.apk",
            "app-good-words-1.6.0-9.exe",
            ""
        )

        for (name in escapes) {
            val failure = assertThrows(IllegalArgumentException::class.java) {
                AppUpdateJson.parse(json(fileName = name))
            }
            assertTrue(
                "이름을 거른 이유가 사용자에게 보여야 합니다: ${failure.message}",
                failure.message.orEmpty().contains("설치 파일 이름")
            )
        }
    }

    @Test fun aReleaseWithoutAUsableHashIsRefused() {
        // 해시가 없으면 받은 파일이 그 파일인지 알 길이 없습니다. 그러면 받을 이유도 없습니다.
        for (bad in listOf("", "abc", sha.dropLast(1), sha + "0", "g".repeat(64))) {
            assertThrows(IllegalArgumentException::class.java) {
                AppUpdateJson.parse(json(sha256 = bad))
            }
        }
    }

    @Test fun aHashInCapitalLettersIsTheSameHash() {
        // 대문자로 적어 보내는 것은 공격이 아니라 표기 차이입니다. 여기서 막으면 멀쩡한
        // 업데이트가 영영 안 보입니다. 소문자로 맞춰 두고, 견주는 쪽은 한 가지 꼴만 압니다.
        assertEquals(sha, AppUpdateJson.parse(json(sha256 = sha.uppercase())).sha256)
    }

    @Test fun anImpossibleSizeIsRefusedBeforeAnythingIsDownloaded() {
        // 0이면 받을 것이 없고, 터무니없이 크면 저장 공간을 끝까지 채웁니다.
        assertThrows(IllegalArgumentException::class.java) { AppUpdateJson.parse(json(sizeBytes = 0)) }
        assertThrows(IllegalArgumentException::class.java) { AppUpdateJson.parse(json(sizeBytes = -1)) }
        assertThrows(IllegalArgumentException::class.java) {
            AppUpdateJson.parse(json(sizeBytes = AppUpdateJson.MAX_SIZE_BYTES + 1))
        }
    }

    @Test fun aReleaseWithoutAVersionCodeIsRefused() {
        assertThrows(IllegalArgumentException::class.java) { AppUpdateJson.parse(json(versionCode = 0)) }
    }

    @Test fun brokenJsonSaysSoInsteadOfCrashing() {
        val failure = assertThrows(IllegalStateException::class.java) { AppUpdateJson.parse("<html>404</html>") }
        assertTrue(failure.message.orEmpty().contains("업데이트 정보를 읽지 못했습니다"))
    }

    @Test fun aMissingVersionNameFallsBackToTheNumber() {
        // 보여 주기만 하는 값이라, 비었다고 업데이트를 막지는 않습니다.
        assertEquals("9", AppUpdateJson.parse(json(versionName = "")).versionCode.toString())
        assertEquals("9", AppUpdateJson.parse(json(versionName = "")).versionName)
    }

    @Test fun onlyAHigherNumberCountsAsANewVersion() {
        val latest = AppUpdateJson.parse(json(versionCode = 9, versionName = "1.6.0"))

        assertTrue(AppUpdateJson.decide(currentVersionCode = 8, latest = latest) is AppUpdateState.Available)
        // 같은 번호를 다시 깔아도 달라지는 것이 없습니다.
        assertTrue(AppUpdateJson.decide(currentVersionCode = 9, latest = latest) is AppUpdateState.UpToDate)
        // 서버가 뒤처져 있을 수도 있습니다. 안드로이드도 낮은 번호는 설치를 거절합니다.
        assertTrue(AppUpdateJson.decide(currentVersionCode = 10, latest = latest) is AppUpdateState.UpToDate)
    }

    @Test fun theProgressRatioNeverLeavesTheBar() {
        val info = AppUpdateJson.parse(json(sizeBytes = 100))

        assertEquals(0f, AppUpdateState.Downloading(info, 0).ratio, 0.0001f)
        assertEquals(0.5f, AppUpdateState.Downloading(info, 50).ratio, 0.0001f)
        // 서버가 알려 준 크기가 틀렸더라도 막대가 넘치지는 않게 합니다.
        assertEquals(1f, AppUpdateState.Downloading(info, 200).ratio, 0.0001f)
    }

    @Test fun whatIsBeingDownloadedIsRememberedAndReadBackWhole() {
        // 받는 동안 앱이 꺼져도 시스템은 계속 받습니다. 다시 켰을 때 맞춰 볼 해시를 잃으면 받은 것을 믿을 수 없습니다.
        val pending = AppUpdatePending(downloadId = 42, info = AppUpdateJson.parse(json()))

        assertEquals(pending, AppUpdateJson.decodePending(AppUpdateJson.encodePending(pending)))
    }

    @Test fun aBrokenOrDoubtfulRecordIsForgottenRatherThanTrusted() {
        assertNull(AppUpdateJson.decodePending(""))
        assertNull(AppUpdateJson.decodePending("{"))
        // 번호가 없으면 무엇을 지켜볼지 모릅니다.
        assertNull(AppUpdateJson.decodePending(json()))
        // 저장된 값도 서버에서 받은 것과 같은 검사를 거칩니다. 경로를 벗어나는 이름은 믿지 않습니다.
        val doubtful = AppUpdateJson.encodePending(AppUpdatePending(7, AppUpdateJson.parse(json())))
            .replace("app-good-words-1.6.0-9-19adb169.apk", "../../evil.apk")
        assertNull(AppUpdateJson.decodePending(doubtful))
    }

    @Test fun waitingForTheNetworkIsStillDownloadingNotAFailure() {
        assertEquals(
            AppUpdateDownloadStatus.Step.Receiving(downloadedBytes = 10, waiting = false),
            AppUpdateDownloadStatus.step(DownloadManager.STATUS_RUNNING, 0, 10)
        )
        assertEquals(
            AppUpdateDownloadStatus.Step.Receiving(downloadedBytes = 0, waiting = false),
            AppUpdateDownloadStatus.step(DownloadManager.STATUS_PENDING, 0, 0)
        )
        // 끊기면 안드로이드가 기다렸다가 받은 데부터 이어 받습니다. 실패라고 하면 사용자가 처음부터 다시 누릅니다.
        assertEquals(
            AppUpdateDownloadStatus.Step.Receiving(downloadedBytes = 30, waiting = true),
            AppUpdateDownloadStatus.step(DownloadManager.STATUS_PAUSED, DownloadManager.PAUSED_WAITING_FOR_NETWORK, 30)
        )
        assertEquals(
            AppUpdateDownloadStatus.Step.Received,
            AppUpdateDownloadStatus.step(DownloadManager.STATUS_SUCCESSFUL, 0, 100)
        )
    }

    @Test fun aStoppedDownloadSaysWhyInWords() {
        fun stopped(reason: Int) =
            (AppUpdateDownloadStatus.step(DownloadManager.STATUS_FAILED, reason, 0) as AppUpdateDownloadStatus.Step.Stopped).message

        assertTrue(stopped(404).contains("게시된 설치 파일이 없습니다"))
        assertTrue(stopped(500).contains("HTTP 500"))
        assertTrue(stopped(DownloadManager.ERROR_INSUFFICIENT_SPACE).contains("저장 공간"))
        assertTrue(stopped(DownloadManager.ERROR_CANNOT_RESUME).contains("이어 받지 못했습니다"))
        assertTrue(stopped(DownloadManager.ERROR_UNKNOWN).contains("다시 받아 주세요"))
    }

    @Test fun aDownloadedFileIsMovedInOnlyWhenItsSizeAndHashMatch() {
        val bytes = ByteArray(5_000) { (it * 7).toByte() }
        val info = AppUpdateJson.parse(json(sizeBytes = bytes.size.toLong(), sha256 = sha256(bytes)))
        val source = File.createTempFile("downloaded", ".apk").apply { writeBytes(bytes) }
        val target = File(source.parentFile, "verified-${System.nanoTime()}/app.apk")

        try {
            val verified = AppUpdateFiles.verifyInto(source, target, info)

            assertEquals(target, verified)
            assertArrayEquals(bytes, verified.readBytes())
        } finally {
            source.delete()
            target.parentFile?.deleteRecursively()
        }
    }

    @Test fun aFileThatIsNotWhatTheServerSaidIsThrownAway() {
        val bytes = ByteArray(5_000) { (it * 7).toByte() }
        val source = File.createTempFile("downloaded", ".apk").apply { writeBytes(bytes) }
        val target = File(source.parentFile, "verified-${System.nanoTime()}/app.apk")

        fun failure(info: AppUpdateInfo): String =
            assertThrows(IllegalStateException::class.java) { AppUpdateFiles.verifyInto(source, target, info) }.message.orEmpty()

        try {
            // 바꿔치기와 같은 모양입니다.
            assertTrue(failure(AppUpdateJson.parse(json(sizeBytes = 5_000, sha256 = "b".repeat(64)))).contains("다릅니다"))
            assertFalse("못 믿을 파일이 남아 있습니다.", target.exists())
            // 덜 받은 것과 더 받은 것도 설치하지 않습니다.
            assertTrue(failure(AppUpdateJson.parse(json(sizeBytes = 6_000, sha256 = sha256(bytes)))).contains("끝까지"))
            assertTrue(failure(AppUpdateJson.parse(json(sizeBytes = 4_000, sha256 = sha256(bytes)))).contains("크기보다 큽니다"))
            assertFalse(target.exists())
        } finally {
            source.delete()
            target.parentFile?.deleteRecursively()
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
