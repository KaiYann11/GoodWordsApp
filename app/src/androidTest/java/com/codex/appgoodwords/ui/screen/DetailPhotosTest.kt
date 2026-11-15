package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ContentType
import com.codex.appgoodwords.ui.theme.AppGoodWordsTheme
import org.junit.Rule
import org.junit.Test

/**
 * 보관함 기록의 상세에서 사진은 작은 사진 줄이고 누르면 전체 화면입니다(사용자가 정함, 2026-11-15 — 일기·행복한 순간과 같게).
 *
 * 동기화로 주소가 서버 것(`appgoodwords://`)이 된 사진도 서버 주소와 접속 키로 받습니다. 1.22.0까지는 그 주소를 Coil에
 * 그대로 넘겨 "이미지를 불러오지 못했습니다"로 나왔습니다. 실제로 받아지는 것은 `AttachmentSyncIntegrationTest`가 봅니다.
 */
class DetailPhotosTest {
    @get:Rule val compose = createComposeRule()
    // 아무것도 듣지 않는 자리라 시험이 바깥으로 요청을 내보내지 않습니다.
    private val serverUrl = "https://127.0.0.1:1"
    private val photos = listOf("a", "b").map { "appgoodwords://attachment/${it.repeat(64)}.jpg" }

    @Test
    fun syncedPhotosShowAsASmallRowAndOpenFullScreenAtTheTappedOne() {
        setDetail(serverUrl)
        compose.onNodeWithTag(detailListTag).performScrollToNode(hasTestTag(attachmentThumbnailTag(photos[1])))

        compose.onNodeWithTag(attachmentThumbnailTag(photos[0])).assertExists()
        compose.onNodeWithTag(attachmentThumbnailTag(photos[1])).performClick()

        compose.onNodeWithTag(attachmentViewerTag).assertExists()
        compose.onNodeWithTag(attachmentViewerCountTag, useUnmergedTree = true).assertTextEquals("2 / 2")
    }

    @Test
    fun withoutAServerSyncedPhotosSayTheyNeedOne() {
        setDetail(serverUrl = "")
        compose.onNodeWithTag(detailListTag).performScrollToNode(hasText("서버 연결 필요"))

        compose.onAllNodesWithText("서버 연결 필요").assertCountEquals(2)
        compose.onNodeWithTag(attachmentThumbnailTag(photos[0])).assertDoesNotExist()
    }

    private fun setDetail(serverUrl: String) {
        compose.setContent {
            AppGoodWordsTheme {
                DetailScreen(
                    item = ContentItemEntity(id = 3, type = ContentType.MEMO, title = "사진 메모", body = "", imageUris = photos),
                    confirmedToday = false,
                    onEdit = {},
                    onDelete = {},
                    onConfirm = {},
                    onToggleFavorite = {},
                    serverUrl = serverUrl,
                    apiKey = "synthetic-review-key"
                )
            }
        }
    }
}
