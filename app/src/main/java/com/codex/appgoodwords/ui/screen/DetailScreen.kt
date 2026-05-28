package com.codex.appgoodwords.ui.screen

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.ImageView
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ContentType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.net.URL

@Composable
fun DetailScreen(
    item: ContentItemEntity,
    confirmedToday: Boolean,
    onEdit: (ContentItemEntity) -> Unit,
    onDelete: (ContentItemEntity) -> Unit,
    onConfirm: (ContentItemEntity) -> Unit,
    onToggleFavorite: (ContentItemEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("삭제할까요?") },
            text = { Text("이 게시글과 관련 보관 정보가 기기에서 삭제됩니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete(item)
                    }
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f),
                                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DetailPill(
                                text = item.type.displayLabel(),
                                containerBrush = Brush.horizontalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                    )
                                ),
                                textColor = MaterialTheme.colorScheme.onPrimary
                            )
                            DetailPill(
                                text = item.category.ifBlank { "미분류" },
                                containerBrush = Brush.horizontalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    )
                                ),
                                textColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        DetailPill(
                            text = if (confirmedToday) "읽음" else "안읽음",
                            containerBrush = Brush.horizontalGradient(
                                listOf(
                                    if (confirmedToday) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant,
                                    if (confirmedToday) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primaryContainer
                                )
                            ),
                            textColor = if (confirmedToday) {
                                MaterialTheme.colorScheme.onSecondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (item.title.isNotBlank()) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text(
                                text = item.body.ifBlank { "본문 메모가 없습니다." },
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (item.author.isNotBlank()) {
                            Text(
                                text = "출처 · ${item.author}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        if (item.type == ContentType.VIDEO && item.sourceUrl.isNotBlank()) {
            item {
                VideoLinkPreview(
                    sourceUrl = item.sourceUrl,
                    thumbnailUrl = item.thumbnailUrl
                )
            }
        }

        if (item.imageUris.isNotEmpty() || item.videoUris.isNotEmpty()) {
            item {
                SectionCard(title = "첨부 미디어") {
                    if (item.imageUris.isNotEmpty()) {
                        Text(
                            text = "사진",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        item.imageUris.forEach { imageUri ->
                            MediaImage(uriString = imageUri)
                        }
                    }

                    if (item.videoUris.isNotEmpty()) {
                        Text(
                            text = "영상",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        item.videoUris.forEach { videoUri ->
                            MediaVideo(uriString = videoUri)
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "정보") {
                MetaRow(label = "읽음 처리 수", value = "${item.showCount}회")
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                MetaRow(
                    label = "최근 읽음",
                    value = item.lastShownAt?.let(::formatDateTime) ?: "아직 없음"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                MetaRow(label = "생성일", value = formatDateTime(item.createdAt))

                if (item.tags.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "태그",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(item.tags) { tag ->
                                DetailPill(
                                    text = "#$tag",
                                    containerBrush = Brush.horizontalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                        )
                                    ),
                                    textColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (item.sourceUrl.isNotBlank()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "원본 링크",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text(
                                text = item.sourceUrl,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "빠른 작업") {
                Button(
                    onClick = { onConfirm(item) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (confirmedToday) "안읽음으로 변경" else "읽음으로 변경")
                }

                OutlinedButton(
                    onClick = { shareContentItem(context, item) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("공유하기")
                }

                if (item.sourceUrl.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.sourceUrl))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            try {
                                context.startActivity(intent)
                            } catch (_: ActivityNotFoundException) {
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("원본 링크 열기")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onEdit(item) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("편집")
                    }

                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("삭제")
                    }
                }

                OutlinedButton(
                    onClick = { onToggleFavorite(item) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (item.isFavorite) "즐겨찾기 해제" else "즐겨찾기 추가")
                }
            }
        }
    }
}

@Composable
private fun VideoLinkPreview(
    sourceUrl: String,
    thumbnailUrl: String
) {
    val context = LocalContext.current
    val previewUrl = remember(sourceUrl, thumbnailUrl) {
        thumbnailUrl.takeIf { it.isNotBlank() } ?: deriveVideoThumbnailUrl(sourceUrl)
    }

    if (previewUrl == null) return

    SectionCard(title = "영상 링크") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "썸네일을 누르면 원본 영상 페이지로 이동합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(sourceUrl))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                        }
                    },
                factory = { viewContext ->
                    ImageView(viewContext).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        adjustViewBounds = true
                        loadImageFromSource(previewUrl)
                    }
                },
                update = { imageView ->
                    imageView.loadImageFromSource(previewUrl)
                }
            )
            Text(
                text = sourceUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MediaImage(uriString: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            factory = { context ->
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    adjustViewBounds = true
                    loadImageFromSource(uriString)
                }
            },
            update = { imageView ->
                imageView.loadImageFromSource(uriString)
            }
        )
        Text(
            text = displayNameFromUri(uriString),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MediaVideo(uriString: String) {
    val uri = remember(uriString) { Uri.parse(uriString) }
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        factory = { context ->
            VideoView(context).apply {
                val controller = MediaController(context)
                controller.setAnchorView(this)
                setMediaController(controller)
                setVideoURI(uri)
                setOnPreparedListener { mediaPlayer ->
                    mediaPlayer.isLooping = false
                }
            }
        },
        update = { videoView ->
            videoView.setVideoURI(uri)
        }
    )
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            content()
        }
    }
}

@Composable
private fun MetaRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DetailPill(
    text: String,
    containerBrush: Brush,
    textColor: androidx.compose.ui.graphics.Color
) {
    Box(
        modifier = Modifier
            .background(containerBrush, RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = textColor
        )
    }
}

private fun formatDateTime(timestamp: Long): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}

private fun ContentType.displayLabel(): String {
    return when (this) {
        ContentType.QUOTE -> "글귀"
        ContentType.LINK -> "링크"
        ContentType.VIDEO -> "영상"
    }
}

private fun ImageView.loadImageFromSource(source: String) {
    if (source.startsWith("http://") || source.startsWith("https://")) {
        setImageDrawable(null)
        val requestedSource = source
        tag = requestedSource
        Thread {
            val bitmap = runCatching {
                URL(requestedSource).openStream().use(BitmapFactory::decodeStream)
            }.getOrNull()
            post {
                if (tag == requestedSource && bitmap != null) {
                    setImageBitmap(bitmap)
                }
            }
        }.start()
    } else {
        setImageURI(Uri.parse(source))
    }
}

private fun deriveVideoThumbnailUrl(sourceUrl: String): String? {
    val uri = Uri.parse(sourceUrl)
    val host = uri.host.orEmpty().lowercase()
    val pathSegments = uri.pathSegments

    val videoId = when {
        "youtu.be" in host -> pathSegments.firstOrNull()
        "youtube.com" in host && uri.getQueryParameter("v") != null -> uri.getQueryParameter("v")
        "youtube.com" in host && pathSegments.firstOrNull() == "shorts" -> pathSegments.getOrNull(1)
        "youtube.com" in host && pathSegments.firstOrNull() == "embed" -> pathSegments.getOrNull(1)
        else -> null
    }?.substringBefore("?")

    return videoId?.takeIf { it.isNotBlank() }?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" }
}

private fun displayNameFromUri(uriString: String): String {
    val uri = Uri.parse(uriString)
    return uri.lastPathSegment
        ?.substringAfterLast('/')
        ?.substringAfterLast(':')
        ?.takeIf(String::isNotBlank)
        ?: "첨부 파일"
}
