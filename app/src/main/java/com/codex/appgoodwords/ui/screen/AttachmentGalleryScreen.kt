package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.codex.appgoodwords.data.AttachmentGallery
import com.codex.appgoodwords.data.AttachmentKind
import com.codex.appgoodwords.data.AttachmentShot
import com.codex.appgoodwords.data.AttachmentUris

internal const val galleryGridTag = "gallery_grid"

/**
 * 붙여 둔 첨부를 한자리에 늘어놓습니다.
 *
 * 일기에 사진을 붙여 두고도 그날 일기를 기억해 내야만 다시 볼 수 있었습니다.
 * 여기서는 달별로 묶어 훑고, 누르면 그 사진이 붙어 있던 기록으로 데려갑니다.
 */
@Composable
fun AttachmentGalleryScreen(
    shots: List<AttachmentShot>,
    onOpenSource: (AttachmentShot) -> Unit,
    modifier: Modifier = Modifier,
    /** 서버가 보관하는 첨부를 받아올 주소. 서버를 안 쓰면 비어 있고, 그 첨부는 자리만 보입니다. */
    serverUrl: String = "",
    apiKey: String = ""
) {
    val byMonth = remember(shots) { AttachmentGallery.byMonth(shots) }

    if (shots.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            EmptyCard(
                title = "아직 붙여 둔 첨부가 없습니다.",
                body = "일기에 사진·동영상·소리를 붙이거나 글귀에 사진을 넣으면 여기에 모입니다."
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier
            .fillMaxSize()
            .testTag(galleryGridTag),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        byMonth.forEach { (month, monthShots) ->
            item(key = "head-$month", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "${month.year}년 ${month.monthValue}월 · ${monthShots.size}장",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                )
            }

            items(monthShots, key = { it.uri }) { shot ->
                AttachmentTile(
                    shot = shot,
                    serverUrl = serverUrl,
                    apiKey = apiKey,
                    onClick = { onOpenSource(shot) }
                )
            }
        }
    }
}

/**
 * 한 칸.
 *
 * 사진만 그림으로 보여 주고, 동영상과 소리는 이름표만 둡니다. 목록에서 영상 미리보기를 만들면
 * 스무 칸을 그릴 때마다 디코더를 스무 번 여는 셈이라 굴리기가 무거워집니다.
 */
@Composable
private fun AttachmentTile(
    shot: AttachmentShot,
    serverUrl: String,
    apiKey: String,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (shot.kind == AttachmentKind.IMAGE) {
            // 기기 안 파일은 그 주소로 바로 읽고, 서버가 보관하는 파일은 http 주소로 바꿔 받습니다.
            // 서버 첨부는 API 키가 필요해서 헤더를 실어 보냅니다. 주소에 키를 붙이면 기록에 남습니다.
            val model = remember(shot.uri, serverUrl, apiKey) {
                val target = AttachmentUris.toHttpUrl(serverUrl, shot.uri)
                    ?: shot.uri.takeIf { AttachmentUris.isLocal(it) }
                target?.let { data ->
                    ImageRequest.Builder(context)
                        .data(data)
                        .apply { if (apiKey.isNotBlank()) addHeader("X-API-Key", apiKey.trim()) }
                        .crossfade(true)
                        .build()
                }
            }
            if (model == null) {
                TileLabel("서버 연결 필요")
            } else {
                SubcomposeAsyncImage(
                    model = model,
                    contentDescription = shot.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = { TileLabel("열 수 없음") }
                )
            }
        } else {
            TileLabel(shot.kind.label)
        }
    }
}

@Composable
private fun TileLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(6.dp)
    )
}
