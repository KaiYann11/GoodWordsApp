package com.codex.appgoodwords.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.ContentDraft
import com.codex.appgoodwords.data.LinkMetadata

@Composable
fun AddContentScreen(
    categories: List<String>,
    existingTags: List<String>,
    sharedText: String?,
    initialDraft: ContentDraft?,
    formVersion: Int,
    submitLabel: String,
    secondaryActionLabel: String?,
    onSecondaryAction: (() -> Unit)?,
    onSharedTextConsumed: () -> Unit,
    onSave: (ContentDraft) -> Unit,
    onFetchMetadata: (String, (LinkMetadata) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isEditing = (initialDraft?.id ?: 0L) != 0L
    val suggestedCategories = remember(categories) {
        (categories + listOf("동기부여", "습관", "집중", "성장", "건강", "커리어"))
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
    }

    var title by rememberSaveable(formVersion) { mutableStateOf(initialDraft?.title.orEmpty()) }
    var body by rememberSaveable(formVersion) { mutableStateOf(initialDraft?.body.orEmpty()) }
    var author by rememberSaveable(formVersion) { mutableStateOf(initialDraft?.author.orEmpty()) }
    var sourceUrl by rememberSaveable(formVersion) { mutableStateOf(initialDraft?.sourceUrl.orEmpty()) }
    var thumbnailUrl by rememberSaveable(formVersion) { mutableStateOf(initialDraft?.thumbnailUrl.orEmpty()) }
    var category by rememberSaveable(formVersion) { mutableStateOf(initialDraft?.category.orEmpty()) }
    var tagsText by rememberSaveable(formVersion) {
        mutableStateOf(initialDraft?.tags?.joinToString(", ").orEmpty())
    }
    var imageUris by rememberSaveable(formVersion) { mutableStateOf(initialDraft?.imageUris ?: emptyList()) }
    var videoUris by rememberSaveable(formVersion) { mutableStateOf(initialDraft?.videoUris ?: emptyList()) }
    var isFavorite by rememberSaveable(formVersion) { mutableStateOf(initialDraft?.isFavorite ?: false) }

    val selectedTags = remember(tagsText) { parseTags(tagsText) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val granted = uris
            .mapNotNull { uri ->
                context.persistReadPermission(uri)
                uri.toString().takeIf(String::isNotBlank)
            }
        imageUris = (imageUris + granted).distinct()
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val granted = uris
            .mapNotNull { uri ->
                context.persistReadPermission(uri)
                uri.toString().takeIf(String::isNotBlank)
            }
        videoUris = (videoUris + granted).distinct()
    }

    LaunchedEffect(sharedText, isEditing) {
        if (isEditing) return@LaunchedEffect

        val text = sharedText.orEmpty().trim()
        if (text.isNotBlank()) {
            if (text.startsWith("http://") || text.startsWith("https://")) {
                sourceUrl = text
            } else if (body.isBlank()) {
                body = text
            }
            onSharedTextConsumed()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = if (isEditing) {
                    "기존 게시글을 수정 중입니다. 저장하면 같은 항목에 반영됩니다."
                } else {
                    "텍스트, 링크, 사진, 영상을 한 화면에서 같이 추가할 수 있습니다. 링크를 넣으면 자동으로 링크/영상 항목으로 분류됩니다."
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }

        item {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("제목") },
                supportingText = { Text("비워두면 본문이나 첨부 파일 이름으로 자동 생성됩니다.") }
            )
        }

        item {
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("본문 / 메모") },
                minLines = 4
            )
        }

        item {
            OutlinedTextField(
                value = author,
                onValueChange = { author = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("저자 / 출처 메모") }
            )
        }

        item {
            OutlinedTextField(
                value = sourceUrl,
                onValueChange = { sourceUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("링크 주소") },
                supportingText = { Text("유튜브 같은 영상 링크를 넣으면 자동으로 영상 항목으로 분류됩니다.") }
            )
        }

        item {
            TextButton(
                onClick = {
                    onFetchMetadata(sourceUrl) { metadata ->
                        if (title.isBlank()) {
                            title = metadata.title
                        }
                        if (body.isBlank()) {
                            body = metadata.description
                        }
                        if (thumbnailUrl.isBlank()) {
                            thumbnailUrl = metadata.thumbnailUrl
                        }
                    }
                },
                enabled = sourceUrl.isNotBlank()
            ) {
                Text("링크 메타데이터 가져오기")
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "첨부",
                    style = MaterialTheme.typography.titleSmall
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { imagePicker.launch(arrayOf("image/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("사진 추가")
                    }
                    OutlinedButton(
                        onClick = { videoPicker.launch(arrayOf("video/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("영상 추가")
                    }
                }
            }
        }

        if (imageUris.isNotEmpty()) {
            item {
                AttachmentChips(
                    title = "선택된 사진",
                    values = imageUris,
                    onRemove = { target -> imageUris = imageUris - target }
                )
            }
        }

        if (videoUris.isNotEmpty()) {
            item {
                AttachmentChips(
                    title = "선택된 영상",
                    values = videoUris,
                    onRemove = { target -> videoUris = videoUris - target }
                )
            }
        }

        item {
            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("카테고리") },
                supportingText = {
                    Text("콘텐츠 종류가 아니라 주제 분류입니다. 예: 동기부여, 습관, 집중, 성장")
                }
            )
        }

        if (suggestedCategories.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(suggestedCategories) { suggestedCategory ->
                        FilterChip(
                            selected = category == suggestedCategory,
                            onClick = { category = suggestedCategory },
                            label = { Text(suggestedCategory) }
                        )
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = tagsText,
                onValueChange = { tagsText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("태그") },
                supportingText = { Text("쉼표로 구분해서 입력합니다.") }
            )
        }

        if (existingTags.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "빠른 태그",
                        style = MaterialTheme.typography.titleSmall
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(existingTags) { tag ->
                            FilterChip(
                                selected = tag in selectedTags,
                                onClick = { tagsText = toggleTag(tagsText, tag) },
                                label = { Text(tag) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("즐겨찾기로 저장")
                Switch(
                    checked = isFavorite,
                    onCheckedChange = { isFavorite = it }
                )
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (secondaryActionLabel != null && onSecondaryAction != null) {
                    OutlinedButton(
                        onClick = onSecondaryAction,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(secondaryActionLabel)
                    }
                }

                Button(
                    onClick = {
                        onSave(
                            ContentDraft(
                                title = title,
                                body = body,
                                author = author,
                                sourceUrl = sourceUrl,
                                thumbnailUrl = thumbnailUrl,
                                category = category,
                                tags = parseTags(tagsText),
                                imageUris = imageUris,
                                videoUris = videoUris,
                                isFavorite = isFavorite
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(submitLabel)
                }
            }
        }
    }
}

@Composable
private fun AttachmentChips(
    title: String,
    values: List<String>,
    onRemove: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(values) { value ->
                FilterChip(
                    selected = true,
                    onClick = { onRemove(value) },
                    label = { Text(displayNameFromUri(value)) }
                )
            }
        }
        Text(
            text = "칩을 누르면 첨부에서 제거됩니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun parseTags(tagsText: String): List<String> {
    return tagsText.split(",")
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
}

private fun toggleTag(tagsText: String, tag: String): String {
    val currentTags = parseTags(tagsText)
    val updated = if (tag in currentTags) {
        currentTags - tag
    } else {
        currentTags + tag
    }
    return updated.joinToString(", ")
}

private fun displayNameFromUri(uriString: String): String {
    val uri = Uri.parse(uriString)
    return uri.lastPathSegment
        ?.substringAfterLast('/')
        ?.substringAfterLast(':')
        ?.takeIf(String::isNotBlank)
        ?: "첨부 파일"
}

private fun android.content.Context.persistReadPermission(uri: Uri) {
    runCatching {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
}
