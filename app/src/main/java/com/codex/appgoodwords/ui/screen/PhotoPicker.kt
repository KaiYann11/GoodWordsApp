package com.codex.appgoodwords.ui.screen

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * 사진 고르기와 고른 사진 — 행복한 순간·좋아하는 것·메모와 할 일 고치기가 같이 씁니다. 앱을 다시 켜도 그 사진을 읽을 수
 * 있게 권한을 붙들어 둡니다(보관함 담기와 같게). 올리는 것은 동기화가 맡습니다 — 보관함 기록의 사진과 같은 길입니다.
 *
 * @param addTag 고르기 단추의 시험 표식. 쓰는 화면마다 다릅니다.
 */
@Composable
internal fun PhotoPicker(
    images: List<String>,
    onChange: (List<String>) -> Unit,
    enabled: Boolean,
    serverUrl: String,
    apiKey: String,
    addTag: String
) {
    val pickPhotos = rememberPhotoPicker(images = images, onChange = onChange)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PickedPhotos(images = images, onChange = onChange, enabled = enabled, serverUrl = serverUrl, apiKey = apiKey)
        OutlinedButton(
            onClick = pickPhotos,
            enabled = enabled,
            modifier = Modifier.testTag(addTag)
        ) { Text(if (images.isEmpty()) "+ 사진" else "+ 사진 더") }
    }
}

/**
 * 사진 고르기 창을 여는 손잡이. 고른 사진은 권한을 붙들어 둔 뒤 [images] 뒤에 더해 [onChange]로 넘깁니다.
 * 고르기 단추를 따로 두는 화면(할 일 적는 줄)이 씁니다.
 */
@Composable
internal fun rememberPhotoPicker(images: List<String>, onChange: (List<String>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val granted = uris.mapNotNull { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            uri.toString().takeIf(String::isNotBlank)
        }
        onChange((images + granted).distinct())
    }
    return { picker.launch(arrayOf("image/*")) }
}

/** 고른 사진들. 한 장마다 ×로 뺍니다. 고른 것이 없으면 아무것도 그리지 않습니다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PickedPhotos(
    images: List<String>,
    onChange: (List<String>) -> Unit,
    enabled: Boolean,
    serverUrl: String,
    apiKey: String
) {
    if (images.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        images.forEach { uri ->
            Box {
                PhotoThumbnail(uri = uri, serverUrl = serverUrl, apiKey = apiKey, size = 64.dp)
                IconButton(
                    onClick = { onChange(images - uri) },
                    enabled = enabled,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "사진 빼기", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** 작은 사진 한 장. 서버에 올라간 사진은 접속 키를 실어 받습니다([attachmentImageRequest]). */
@Composable
internal fun PhotoThumbnail(uri: String, serverUrl: String, apiKey: String, size: Dp) {
    val context = LocalContext.current
    val model = remember(uri, serverUrl, apiKey) { attachmentImageRequest(context, uri, serverUrl, apiKey) }
    if (model == null) {
        // 서버 주소를 안 넣은 기기에서 다른 기기가 붙인 사진을 받으면 여기로 옵니다.
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text("사진", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        AsyncImage(
            model = model,
            imageLoader = attachmentImageLoader(),
            contentDescription = "붙여 둔 사진",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}
