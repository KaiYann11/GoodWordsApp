package com.codex.appgoodwords.ui.screen

import android.app.TimePickerDialog
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.ReminderSettings
import com.codex.appgoodwords.data.ServerSyncSettings
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    settings: ReminderSettings,
    serverSyncSettings: ServerSyncSettings,
    categories: List<String>,
    onSettingsChanged: (ReminderSettings) -> Unit,
    onServerSyncSettingsChanged: (ServerSyncSettings) -> Unit,
    onSendTestNotification: () -> Unit,
    onResetViewCounts: () -> Unit,
    onExportRequested: (Uri) -> Unit,
    onImportRequested: (Uri) -> Unit,
    onUploadToServer: () -> Unit,
    onDownloadFromServer: () -> Unit,
    onDeleteCategory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            onExportRequested(uri)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportRequested(uri)
        }
    }

    var intervalMinutesText by rememberSaveable { mutableStateOf(settings.intervalMinutes.toString()) }
    var serverUrlText by rememberSaveable { mutableStateOf(serverSyncSettings.serverUrl) }
    var serverApiKeyText by rememberSaveable { mutableStateOf(serverSyncSettings.apiKey) }

    LaunchedEffect(settings.intervalMinutes) {
        val normalized = settings.intervalMinutes.toString()
        if (intervalMinutesText != normalized) {
            intervalMinutesText = normalized
        }
    }

    LaunchedEffect(serverSyncSettings.serverUrl) {
        if (serverUrlText != serverSyncSettings.serverUrl) {
            serverUrlText = serverSyncSettings.serverUrl
        }
    }

    LaunchedEffect(serverSyncSettings.apiKey) {
        if (serverApiKeyText != serverSyncSettings.apiKey) {
            serverApiKeyText = serverSyncSettings.apiKey
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("반복 노출", style = MaterialTheme.typography.titleMedium)
                    SettingSwitchRow(
                        title = "주기 알림 사용",
                        checked = settings.remindersEnabled,
                        onCheckedChange = {
                            onSettingsChanged(settings.copy(remindersEnabled = it))
                        }
                    )
                    SettingSwitchRow(
                        title = "앱 실행 시 오늘의 글귀 표시",
                        checked = settings.showOnLaunch,
                        onCheckedChange = {
                            onSettingsChanged(settings.copy(showOnLaunch = it))
                        }
                    )
                    SettingSwitchRow(
                        title = "잠금화면 알림 내용 공개",
                        checked = settings.lockScreenVisible,
                        onCheckedChange = {
                            onSettingsChanged(settings.copy(lockScreenVisible = it))
                        }
                    )
                    SettingSwitchRow(
                        title = "알림 소리 사용",
                        checked = settings.notificationSoundEnabled,
                        onCheckedChange = {
                            onSettingsChanged(settings.copy(notificationSoundEnabled = it))
                        }
                    )
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("반복 간격", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = intervalMinutesText,
                        onValueChange = { value ->
                            val filtered = value.filter(Char::isDigit)
                            intervalMinutesText = filtered
                            filtered.toIntOrNull()?.let { parsed ->
                                onSettingsChanged(
                                    settings.copy(
                                        intervalMinutes = parsed.coerceAtLeast(ReminderSettings.MIN_INTERVAL_MINUTES)
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("반복 간격(분)") },
                        supportingText = { Text("최소 15분, 예: 15 / 30 / 90") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(15, 30, 60, 90, 180, 360, 720, 1440)) { minutes ->
                            FilterChip(
                                selected = settings.intervalMinutes == minutes,
                                onClick = {
                                    intervalMinutesText = minutes.toString()
                                    onSettingsChanged(settings.copy(intervalMinutes = minutes))
                                },
                                label = { Text(formatInterval(minutes)) }
                            )
                        }
                    }

                    Text(
                        text = "반복 시작 시간부터 종료 시간 사이에서만 알림을 보냅니다.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Button(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    onSettingsChanged(
                                        settings.copy(
                                            preferredHour = hour,
                                            preferredMinute = minute
                                        )
                                    )
                                },
                                settings.preferredHour,
                                settings.preferredMinute,
                                true
                            ).show()
                        }
                    ) {
                        Text("반복 시작 시간 ${formatTime(settings.preferredHour, settings.preferredMinute)}")
                    }

                    Button(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    onSettingsChanged(
                                        settings.copy(
                                            repeatEndHour = hour,
                                            repeatEndMinute = minute
                                        )
                                    )
                                },
                                settings.repeatEndHour,
                                settings.repeatEndMinute,
                                true
                            ).show()
                        }
                    ) {
                        Text("반복 종료 시간 ${formatTime(settings.repeatEndHour, settings.repeatEndMinute)}")
                    }
                }
            }
        }

        item {
            Text("카테고리 필터", style = MaterialTheme.typography.titleMedium)
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = settings.categoryFilter.isBlank(),
                        onClick = {
                            onSettingsChanged(settings.copy(categoryFilter = ""))
                        },
                        label = { Text("전체") }
                    )
                }
                items(categories) { category ->
                    FilterChip(
                        selected = settings.categoryFilter == category,
                        onClick = {
                            onSettingsChanged(settings.copy(categoryFilter = category))
                        },
                        label = { Text(category) }
                    )
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("카테고리 관리", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "카테고리를 삭제하면 해당 카테고리를 쓰던 항목은 미분류로 바뀝니다.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (categories.isEmpty()) {
                        Text(
                            text = "삭제할 카테고리가 없습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        categories.forEach { category ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(category, style = MaterialTheme.typography.bodyLarge)
                                OutlinedButton(onClick = { onDeleteCategory(category) }) {
                                    Text("삭제")
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("일일 집계", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "하루 동안 노출된 항목과 확인한 항목을 매일 한 번 리스트 형태로 집계해 알립니다.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    SettingSwitchRow(
                        title = "일일 집계 알림 사용",
                        checked = settings.dailySummaryEnabled,
                        onCheckedChange = {
                            onSettingsChanged(settings.copy(dailySummaryEnabled = it))
                        }
                    )
                    Button(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    onSettingsChanged(
                                        settings.copy(
                                            summaryHour = hour,
                                            summaryMinute = minute
                                        )
                                    )
                                },
                                settings.summaryHour,
                                settings.summaryMinute,
                                true
                            ).show()
                        }
                    ) {
                        Text("집계 발송 시간 ${formatTime(settings.summaryHour, settings.summaryMinute)}")
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("데이터 관리", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "저장된 항목, 이력, 오늘 확인 기록, 현재 알림 설정을 JSON 파일로 백업하거나 복원합니다.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = onResetViewCounts,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("읽음 처리 수 초기화")
                    }
                    Button(
                        onClick = { exportLauncher.launch(defaultExportFileName()) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("데이터 내보내기")
                    }
                    Button(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("데이터 가져오기")
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("서버 동기화", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = serverUrlText,
                        onValueChange = { value ->
                            serverUrlText = value
                            onServerSyncSettingsChanged(
                                ServerSyncSettings(
                                    serverUrl = value,
                                    apiKey = serverApiKeyText
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("서버 주소") },
                        supportingText = { Text("예: http://10.0.2.2:8765 또는 http://192.168.0.10:8765") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = serverApiKeyText,
                        onValueChange = { value ->
                            serverApiKeyText = value
                            onServerSyncSettingsChanged(
                                ServerSyncSettings(
                                    serverUrl = serverUrlText,
                                    apiKey = value
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API 키") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    Button(
                        onClick = onUploadToServer,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = serverUrlText.isNotBlank()
                    ) {
                        Text("서버로 업로드")
                    }
                    OutlinedButton(
                        onClick = onDownloadFromServer,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = serverUrlText.isNotBlank()
                    ) {
                        Text("서버에서 가져오기")
                    }
                }
            }
        }

        item {
            Button(
                onClick = onSendTestNotification,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("테스트 알림 보내기")
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

private fun formatTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

private fun formatInterval(intervalMinutes: Int): String {
    return when {
        intervalMinutes % 60 == 0 -> "${intervalMinutes / 60}시간"
        intervalMinutes > 60 -> "${intervalMinutes / 60}시간 ${intervalMinutes % 60}분"
        else -> "${intervalMinutes}분"
    }
}

private fun defaultExportFileName(): String {
    val timestamp = LocalDateTime.now()
        .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
    return "app-good-words-export-$timestamp.json"
}
