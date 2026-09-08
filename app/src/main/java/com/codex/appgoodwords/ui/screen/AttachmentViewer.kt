package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.codex.appgoodwords.data.AttachmentUris

internal const val attachmentViewerTag = "attachment_viewer"
internal const val attachmentViewerPageTag = "attachment_viewer_page"
internal const val attachmentViewerCountTag = "attachment_viewer_count"

internal fun attachmentThumbnailTag(uri: String) = "attachment_thumbnail_$uri"

/**
 * 붙여 둔 사진을 화면 가득 봅니다.
 *
 * 목록의 미리보기는 96dp 정사각형으로 **잘라서** 보여 줍니다. 가로로 긴 사진이나 글씨가 든
 * 사진은 그 안에서 무엇인지 알아볼 수 없습니다. 여기서는 자르지 않고([ContentScale.Fit])
 * 원래 비율 그대로 채웁니다.
 *
 * **옆으로 밀어 다음 사진으로 넘어갑니다.** 한 장만 보여 주고 닫게 하면, 다섯 장 붙인 날에는
 * 닫고 누르기를 다섯 번 되풀이해야 합니다.
 *
 * 아무 데나 누르면 닫힙니다. 사진을 볼 때는 화면에 아무것도 없는 편이 낫고, 닫는 버튼을 따로
 * 두면 그 버튼이 사진을 가립니다.
 */
@Composable
fun AttachmentViewerDialog(
    uris: List<String>,
    startIndex: Int,
    onDismiss: () -> Unit,
    serverUrl: String = "",
    apiKey: String = ""
) {
    if (uris.isEmpty()) return

    val startPage = startIndex.coerceIn(0, uris.lastIndex)
    val pagerState = rememberPagerState(initialPage = startPage) { uris.size }

    Dialog(
        onDismissRequest = onDismiss,
        // 기본 너비에 맞추면 대화상자만 해집니다. "전체 보기"가 아니게 됩니다.
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag(attachmentViewerTag)
                // 물결 효과 없이 닫습니다. 사진 위에 동그란 자국이 도는 것은 어울리지 않습니다.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                AttachmentPage(
                    uri = uris[page],
                    serverUrl = serverUrl,
                    apiKey = apiKey
                )
            }

            // 몇 장 중 몇 번째인지. 이것이 없으면 옆으로 밀 수 있다는 것을 모릅니다.
            if (uris.size > 1) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${uris.size}",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp)
                        .testTag(attachmentViewerCountTag),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun AttachmentPage(
    uri: String,
    serverUrl: String,
    apiKey: String
) {
    val context = LocalContext.current
    val model = remember(uri, serverUrl, apiKey) {
        val target = AttachmentUris.toHttpUrl(serverUrl, uri) ?: uri.takeIf { AttachmentUris.isLocal(it) }
        target?.let { data ->
            ImageRequest.Builder(context)
                .data(data)
                .apply { if (apiKey.isNotBlank()) addHeader("X-API-Key", apiKey.trim()) }
                .crossfade(true)
                .build()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(attachmentViewerPageTag),
        contentAlignment = Alignment.Center
    ) {
        if (model == null) {
            // 서버 주소를 안 넣은 채 다른 기기에서 붙인 첨부를 받으면 여기로 옵니다.
            Text(
                text = "서버에 연결해야 볼 수 있는 사진입니다.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        } else {
            SubcomposeAsyncImage(
                model = model,
                contentDescription = "붙여 둔 사진",
                modifier = Modifier.fillMaxSize(),
                // 자르지 않습니다. 전체를 보려고 연 화면입니다.
                contentScale = ContentScale.Fit,
                error = {
                    Text(
                        text = "사진을 불러오지 못했습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            )
        }
    }
}
