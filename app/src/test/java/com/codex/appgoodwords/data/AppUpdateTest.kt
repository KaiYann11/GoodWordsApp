package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
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
}
