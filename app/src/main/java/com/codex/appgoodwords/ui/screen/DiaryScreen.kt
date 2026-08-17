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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.codex.appgoodwords.data.AttachmentUris
import com.codex.appgoodwords.data.DiaryDraft
import com.codex.appgoodwords.data.DiaryEntity
import com.codex.appgoodwords.data.DiaryMood
import com.codex.appgoodwords.data.DiaryWeather
import java.time.LocalDate

internal const val diaryWriteButtonTag = "diary_write_button"
internal const val diaryBodyTag = "diary_body"
internal const val diarySaveButtonTag = "diary_save_button"

/** 날씨·기분 칩은 개수가 많아 하나씩 태그를 답니다. 테스트에서 특정 칩만 누르기 위한 것입니다. */
internal fun diaryWeatherChipTag(weather: DiaryWeather) = "diary_weather_${weather.name}"

internal fun diaryMoodChipTag(mood: DiaryMood) = "diary_mood_${mood.name}"

/**
 * 날짜별 일기.
 *
 * 첨부는 URI만 들고 있습니다. 파일은 앱 밖에 있어서, 원본이 지워지면 첨부도 열리지 않습니다.
 * 다른 기기로 동기화해도 파일 자체는 가지 않습니다.
 */
@Composable
fun DiaryScreen(
    diaries: List<DiaryEntity>,
    today: LocalDate,
    onSaveDiary: (DiaryDraft) -> Unit,
    onDeleteDiary: (Long) -> Unit,
    modifier: Modifier = Modifier,
    /** 서버가 보관하는 첨부를 받아올 주소. 서버를 안 쓰면 비어 있고, 그 첨부는 자리만 보입니다. */
    serverUrl: String = "",
    apiKey: String = ""
) {
    var editing by remember { mutableStateOf<DiaryDraft?>(null) }
    var pendingDelete by remember { mutableStateOf<DiaryEntity?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("일기", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "날씨와 기분을 고르고 사진·동영상·음성 파일을 붙일 수 있습니다. " +
                            "하루에 여러 번 써도 됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { editing = DiaryDraft(entryDate = today) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(diaryWriteButtonTag)
                    ) {
                        Text("오늘 일기 쓰기")
                    }
                }
            }
        }

        if (diaries.isEmpty()) {
            item {
                Text(
                    text = "아직 쓴 일기가 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(diaries, key = { it.id }) { diary ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = listOfNotNull(
                                    diary.entryDate,
                                    diary.weatherOption?.let { "${it.emoji} ${it.label}" },
                                    diary.moodOption?.let { "${it.emoji} ${it.label}" }
                                ).joinToString("  ·  "),
                                style = MaterialTheme.typography.labelMedium
                            )
                            if (diary.displayTitle.isNotBlank()) {
                                Text(diary.displayTitle, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                        TextButton(onClick = { editing = DiaryDraft.from(diary) }) { Text("수정") }
                        IconButton(onClick = { pendingDelete = diary }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "지우기")
                        }
                    }
                    if (diary.body.isNotBlank()) {
                        Text(diary.body, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (diary.hasAttachments) {
                        Text(
                            text = attachmentSummary(diary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AttachmentThumbnails(
                            uris = diary.imageUris + diary.videoUris,
                            serverUrl = serverUrl,
                            apiKey = apiKey
                        )
                    }
                }
            }
        }
    }

    editing?.let { draft ->
        DiaryEditDialog(
            draft = draft,
            onDismiss = { editing = null },
            onSave = {
                onSaveDiary(it)
                editing = null
            }
        )
    }

    pendingDelete?.let { diary ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("일기를 지울까요?") },
            text = { Text("${diary.entryDate} 일기를 지웁니다. 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteDiary(diary.id)
                    pendingDelete = null
                }) {
                    Text("지우기")
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("취소") } }
        )
    }
}

/**
 * 붙여 둔 사진과 영상을 줄지어 보여 줍니다.
 *
 * 기기 안 파일은 그 주소로 바로 읽고, 서버가 보관하는 파일은 http 주소로 바꿔 받습니다.
 * 서버 첨부는 API 키가 필요해서 헤더를 실어 보냅니다. 주소에 키를 붙이면 기록에 남습니다.
 */
@Composable
private fun AttachmentThumbnails(uris: List<String>, serverUrl: String, apiKey: String) {
    if (uris.isEmpty()) return
    val context = LocalContext.current

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(uris) { uri ->
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
            if (model == null) {
                // 서버 주소를 안 넣은 채 다른 기기에서 붙인 첨부를 받으면 여기로 옵니다.
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "서버 연결 필요",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                AsyncImage(
                    model = model,
                    contentDescription = "붙여 둔 첨부",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }
    }
}

private fun attachmentSummary(diary: DiaryEntity): String = buildList {
    if (diary.imageUris.isNotEmpty()) add("사진 ${diary.imageUris.size}")
    if (diary.videoUris.isNotEmpty()) add("동영상 ${diary.videoUris.size}")
    if (diary.audioUris.isNotEmpty()) add("음성 ${diary.audioUris.size}")
}.joinToString(" · ")

@Composable
private fun DiaryEditDialog(
    draft: DiaryDraft,
    onDismiss: () -> Unit,
    onSave: (DiaryDraft) -> Unit
) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(draft) }

    // 앱을 다시 켜도 첨부를 열 수 있으려면 읽기 권한을 붙잡아 둬야 합니다.
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val kept = uris.onEach(context::persistDiaryReadPermission).map(Uri::toString)
        if (kept.isNotEmpty()) current = current.copy(imageUris = current.imageUris + kept)
    }
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val kept = uris.onEach(context::persistDiaryReadPermission).map(Uri::toString)
        if (kept.isNotEmpty()) current = current.copy(videoUris = current.videoUris + kept)
    }
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val kept = uris.onEach(context::persistDiaryReadPermission).map(Uri::toString)
        if (kept.isNotEmpty()) current = current.copy(audioUris = current.audioUris + kept)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${current.entryDate} 일기") },
        text = {
            // 선택 줄까지 들어가면 작은 화면에서는 넘칩니다. 스크롤이 없으면 저장 버튼에 닿지 못합니다.
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = current.title,
                    onValueChange = { current = current.copy(title = it) },
                    label = { Text("제목 (선택)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 같은 칩을 다시 누르면 선택이 풀립니다. 잘못 골랐을 때 되돌릴 방법이 달리 없습니다.
                // 개수가 몇 개뿐이라 LazyRow를 쓰지 않습니다. 화면 밖 칩도 만들어 두어야 스크롤로 닿습니다.
                Text("오늘의 날씨", style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    DiaryWeather.entries.forEach { weather ->
                        val selected = current.weather == weather.name
                        FilterChip(
                            selected = selected,
                            onClick = {
                                current = current.copy(weather = if (selected) "" else weather.name)
                            },
                            label = { Text("${weather.emoji} ${weather.label}") },
                            modifier = Modifier.testTag(diaryWeatherChipTag(weather))
                        )
                    }
                }

                Text("오늘의 기분", style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    DiaryMood.entries.forEach { mood ->
                        val selected = current.mood == mood.name
                        FilterChip(
                            selected = selected,
                            onClick = {
                                current = current.copy(mood = if (selected) "" else mood.name)
                            },
                            label = { Text("${mood.emoji} ${mood.label}") },
                            modifier = Modifier.testTag(diaryMoodChipTag(mood))
                        )
                    }
                }

                OutlinedTextField(
                    value = current.body,
                    onValueChange = { current = current.copy(body = it) },
                    label = { Text("오늘 있었던 일") },
                    minLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(diaryBodyTag)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { imagePicker.launch(arrayOf("image/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("사진")
                    }
                    OutlinedButton(
                        onClick = { videoPicker.launch(arrayOf("video/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("동영상")
                    }
                    OutlinedButton(
                        onClick = { audioPicker.launch(arrayOf("audio/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("음성")
                    }
                }
                val summary = buildList {
                    if (current.imageUris.isNotEmpty()) add("사진 ${current.imageUris.size}")
                    if (current.videoUris.isNotEmpty()) add("동영상 ${current.videoUris.size}")
                    if (current.audioUris.isNotEmpty()) add("음성 ${current.audioUris.size}")
                }.joinToString(" · ")
                if (summary.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "첨부 $summary",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            current = current.copy(
                                imageUris = emptyList(),
                                videoUris = emptyList(),
                                audioUris = emptyList()
                            )
                        }) {
                            Text("첨부 비우기")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = current.hasSomethingToSave,
                onClick = { onSave(current) },
                modifier = Modifier.testTag(diarySaveButtonTag)
            ) {
                Text("저장")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

private fun android.content.Context.persistDiaryReadPermission(uri: Uri) {
    runCatching {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
