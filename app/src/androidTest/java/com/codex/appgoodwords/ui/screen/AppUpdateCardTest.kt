package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.codex.appgoodwords.data.AppUpdateInfo
import com.codex.appgoodwords.data.AppUpdateState
import com.codex.appgoodwords.ui.theme.AppGoodWordsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 설정의 "앱 업데이트" 카드.
 *
 * 여기서 보는 것은 **지금 무엇을 기다리는지 한 줄로 알 수 있는가**와, 설치 버튼이 받아 둔
 * 파일을 가리키는가입니다. 받는 중에 또 누를 수 있으면 같은 파일을 두 번 받습니다.
 */
class AppUpdateCardTest {
    @get:Rule val compose = createComposeRule()

    private val info = AppUpdateInfo(
        versionName = "1.7.0",
        versionCode = 10,
        fileName = "app-good-words-1.7.0-10-abcdef12.apk",
        sizeBytes = 25_010_894,
        sha256 = "a".repeat(64),
        publishedAt = "2026-10-08T00:00:00Z"
    )

    private fun card(
        state: AppUpdateState,
        serverConfigured: Boolean = true,
        onCheck: () -> Unit = {},
        onDownload: () -> Unit = {},
        onInstall: (String) -> Unit = {},
        onDismiss: () -> Unit = {}
    ) {
        compose.setContent {
            AppGoodWordsTheme {
                AppUpdateCard(
                    state = state,
                    currentVersionName = "1.6.0",
                    currentVersionCode = 9,
                    serverConfigured = serverConfigured,
                    onCheck = onCheck,
                    onDownload = onDownload,
                    onInstall = onInstall,
                    onDismiss = onDismiss
                )
            }
        }
    }

    @Test fun theCardAlwaysSaysWhichVersionIsInstalled() {
        card(AppUpdateState.Idle)

        compose.onNodeWithText("지금 버전 1.6.0 (9)").assertIsDisplayed()
        compose.onNodeWithTag(appUpdateCheckTag).assertIsEnabled()
    }

    @Test fun withoutAServerAddressThereIsNothingToCheck() {
        card(AppUpdateState.Idle, serverConfigured = false)

        // 눌러 보고 나서 "서버 주소가 없다"고 알게 하지 않습니다.
        compose.onNodeWithTag(appUpdateCheckTag).assertIsNotEnabled()
        compose.onNodeWithText("서버 주소를 먼저 넣어야 확인할 수 있습니다.").assertIsDisplayed()
    }

    @Test fun aNewVersionShowsItsNumberAndSizeBeforeDownloading() {
        var downloads = 0
        card(AppUpdateState.Available(info), onDownload = { downloads++ })

        compose.onNodeWithText("새 버전 1.7.0 (10)이 있습니다.").assertIsDisplayed()
        // 25MB짜리를 받는 일이라 크기를 먼저 보여 줍니다.
        compose.onNodeWithText("내려받을 크기 23.9 MB. 지금 기록은 그대로 남습니다.").assertIsDisplayed()

        compose.onNodeWithTag(appUpdateDownloadTag).performClick()
        assertEquals(1, downloads)
    }

    @Test fun whileDownloadingThereIsNothingToPress() {
        card(AppUpdateState.Downloading(info, 12_505_447))

        compose.onNodeWithText("내려받는 중… 50%").assertIsDisplayed()
        // 다시 누를 수 있으면 같은 파일을 두 번 받습니다.
        compose.onNodeWithTag(appUpdateDownloadTag).assertDoesNotExist()
        compose.onNodeWithTag(appUpdateCheckTag).assertDoesNotExist()
    }

    @Test fun installHandsOverTheFileThatWasVerified() {
        var installed: String? = null
        card(AppUpdateState.Ready(info, "/data/cache/updates/${info.fileName}"), onInstall = { installed = it })

        compose.onNodeWithText("1.7.0 설치 준비가 끝났습니다.").assertIsDisplayed()
        // 처음 설치할 때 막히는 자리라 미리 알려 둡니다.
        compose.onNodeWithText("이 출처의 앱 설치 허용", substring = true).assertIsDisplayed()

        compose.onNodeWithTag(appUpdateInstallTag).performClick()
        assertEquals("/data/cache/updates/${info.fileName}", installed)
    }

    @Test fun beingUpToDateIsSaidPlainly() {
        card(AppUpdateState.UpToDate("1.6.0"))

        compose.onNodeWithText("이미 최신입니다. 서버에 올라온 것도 1.6.0입니다.").assertIsDisplayed()
        // 최신이어도 다시 물어볼 수 있어야 합니다. 방금 올렸을 수도 있습니다.
        compose.onNodeWithTag(appUpdateCheckTag).assertIsEnabled()
    }

    @Test fun aFailureIsShownAndCanBeRetried() {
        card(AppUpdateState.Failed("서버에 게시된 설치 파일이 없습니다. 서버에서 먼저 새 버전을 올려 주세요."))

        compose.onNodeWithText("서버에 게시된 설치 파일이 없습니다. 서버에서 먼저 새 버전을 올려 주세요.")
            .assertIsDisplayed()
        compose.onNodeWithTag(appUpdateCheckTag).assertIsEnabled()
    }
}
