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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.ReminderSettings
import com.codex.appgoodwords.data.ServerSyncSettings
import com.codex.appgoodwords.data.SyncBackup
import com.codex.appgoodwords.data.SyncStatus
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 설정 화면에는 스위치가 여럿이라 테스트에서 자동 동기화 스위치만 집으려면 표식이 필요하다. */
internal const val autoSyncSwitchTag = "auto_sync_switch"

/** 이력은 하단 바에서 빠졌으므로 여기로 들어가는 길이 있는지 확인해야 한다. */
internal const val historyButtonTag = "history_button"

/** 통계도 하단 바에서 빠졌으므로 설정에서 여는 버튼을 따로 둔다. */
internal const val statsButtonTag = "stats_button"

/** 교체와 병합은 결과가 정반대라 버튼을 헷갈리면 안 된다. */
internal const val fileMergeButtonTag = "file_merge_button"

private sealed interface PendingSyncAction {
    object Merge : PendingSyncAction
    object Upload : PendingSyncAction
    object Download : PendingSyncAction
    data class Restore(val backup: SyncBackup) : PendingSyncAction
}

@Composable
fun SettingsScreen(
    settings: ReminderSettings,
    serverSyncSettings: ServerSyncSettings,
    syncStatus: SyncStatus,
    categories: List<String>,
    syncBackups: List<SyncBackup>,
    syncBackupDirectory: String,
    /** 알림 권한이 없으면 예약은 걸려도 알림이 오지 않으므로 화면에서 알려 줘야 한다. */
    notificationsBlocked: Boolean = false,
    onSettingsChanged: (ReminderSettings) -> Unit,
    onServerSyncSettingsChanged: (ServerSyncSettings) -> Unit,
    onSendTestNotification: () -> Unit,
    onResetViewCounts: () -> Unit,
    onExportRequested: (Uri) -> Unit,
    onImportRequested: (Uri) -> Unit,
    onMergeFileRequested: (Uri) -> Unit = {},
    onTestServerConnection: () -> Unit,
    onSyncWithServer: () -> Unit,
    onUploadToServer: () -> Unit,
    onDownloadFromServer: () -> Unit,
    onRestoreBackup: (SyncBackup) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onOpenStats: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
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
    val fileMergeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onMergeFileRequested(uri)
        }
    }

    var intervalMinutesText by rememberSaveable { mutableStateOf(settings.intervalMinutes.toString()) }
    var serverUrlText by rememberSaveable { mutableStateOf(serverSyncSettings.serverUrl) }
    var serverApiKeyText by rememberSaveable { mutableStateOf(serverSyncSettings.apiKey) }
    // 화면이 다시 만들어지면 확인 대화상자를 닫아 실수로 실행되지 않게 한다.
    var pendingSyncAction by remember { mutableStateOf<PendingSyncAction?>(null) }

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

    pendingSyncAction?.let { action ->
        SyncConfirmDialog(
            action = action,
            onDismiss = { pendingSyncAction = null },
            onConfirm = {
                pendingSyncAction = null
                when (action) {
                    PendingSyncAction.Merge -> onSyncWithServer()
                    PendingSyncAction.Upload -> onUploadToServer()
                    PendingSyncAction.Download -> onDownloadFromServer()
                    is PendingSyncAction.Restore -> onRestoreBackup(action.backup)
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
                    if (settings.remindersEnabled && notificationsBlocked) {
                        Text(
                            text = "알림 권한이 없어 알림이 오지 않습니다. " +
                                "휴대폰 설정 > 앱 > 오늘의 글귀 > 알림에서 켜 주세요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
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
                        Text("데이터 가져오기 (교체)")
                    }
                    OutlinedButton(
                        onClick = { fileMergeLauncher.launch(arrayOf("application/json")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(fileMergeButtonTag)
                    ) {
                        Text("파일과 병합")
                    }
                    Text(
                        text = "가져오기는 기기 데이터를 파일로 통째로 바꿉니다. " +
                            "병합은 양쪽을 항목 단위로 합칩니다. 서버 없이 두 기기를 맞출 때 쓰세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    Text("서버 동기화", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = serverUrlText,
                        onValueChange = { value ->
                            serverUrlText = value
                            onServerSyncSettingsChanged(serverSyncSettings.copy(serverUrl = value))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("서버 주소") },
                        supportingText = {
                            Text("예: http://192.168.0.10:8765 (평문 http는 같은 네트워크 주소만, 외부는 https 필요)")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = serverApiKeyText,
                        onValueChange = { value ->
                            serverApiKeyText = value
                            onServerSyncSettingsChanged(serverSyncSettings.copy(apiKey = value))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API 키") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    OutlinedButton(
                        onClick = onTestServerConnection,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = serverUrlText.isNotBlank()
                    ) {
                        Text("연결 테스트")
                    }
                    Button(
                        onClick = { pendingSyncAction = PendingSyncAction.Merge },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = serverUrlText.isNotBlank()
                    ) {
                        Text("서버와 병합")
                    }
                    Text(
                        text = "병합은 양쪽 변경을 항목 단위로 합칩니다. 여러 기기를 쓴다면 이 방식을 쓰세요.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "아래 두 가지는 한쪽 데이터를 통째로 교체합니다. 한 기기를 기준으로 맞출 때만 쓰세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { pendingSyncAction = PendingSyncAction.Upload },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = serverUrlText.isNotBlank()
                    ) {
                        Text("서버로 업로드")
                    }
                    OutlinedButton(
                        onClick = { pendingSyncAction = PendingSyncAction.Download },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = serverUrlText.isNotBlank()
                    ) {
                        Text("서버에서 가져오기")
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
                    Text("자동 동기화", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("배경에서 주기적으로 병합", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "병합만 실행합니다. 업로드·가져오기처럼 한쪽을 통째로 지우지 않습니다.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            modifier = Modifier.testTag(autoSyncSwitchTag),
                            checked = serverSyncSettings.autoSyncEnabled,
                            onCheckedChange = { enabled ->
                                onServerSyncSettingsChanged(
                                    serverSyncSettings.copy(autoSyncEnabled = enabled)
                                )
                            },
                            enabled = serverUrlText.isNotBlank()
                        )
                    }

                    if (serverUrlText.isBlank()) {
                        Text(
                            text = "서버 주소를 먼저 넣어야 켤 수 있습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (serverSyncSettings.autoSyncEnabled) {
                        Text("주기", style = MaterialTheme.typography.bodyMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ServerSyncSettings.INTERVAL_CHOICES) { hours ->
                                FilterChip(
                                    selected = serverSyncSettings.effectiveIntervalHours == hours,
                                    onClick = {
                                        onServerSyncSettingsChanged(
                                            serverSyncSettings.copy(autoSyncIntervalHours = hours)
                                        )
                                    },
                                    label = { Text("${hours}시간") }
                                )
                            }
                        }
                        Text(
                            text = "네트워크가 연결된 동안에만 돕니다. 안드로이드가 배터리 상태에 따라 조금 늦출 수 있습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 배경 동기화는 실패해도 화면에 뜨지 않으므로 여기서 확인할 수 있어야 한다.
                    Text(
                        text = when {
                            !syncStatus.hasSynced -> "마지막 동기화: 없음"
                            syncStatus.failed ->
                                "마지막 시도: ${formatDateTime(syncStatus.lastSyncAt)} · 실패 (${syncStatus.lastError})"

                            else -> "마지막 동기화: ${formatDateTime(syncStatus.lastSyncAt)} · 성공"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (syncStatus.failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
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
                    Text("동기화 백업", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "병합·업로드·가져오기·복원 직전 상태를 자동으로 저장합니다. 종류별로 최근 5개까지 보관합니다.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (syncBackups.isEmpty()) {
                        Text(
                            text = "아직 저장된 백업이 없습니다.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        syncBackups.forEach { backup ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = backup.kind.label,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "${formatDateTime(backup.createdAt)} · ${formatBackupSize(backup.sizeBytes)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                TextButton(onClick = { pendingSyncAction = PendingSyncAction.Restore(backup) }) {
                                    Text("복원")
                                }
                            }
                        }
                    }
                    if (syncBackupDirectory.isNotBlank()) {
                        Text(
                            text = "저장 위치: $syncBackupDirectory",
                            style = MaterialTheme.typography.bodySmall
                        )
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

        // 통계와 이력은 매일 볼 화면이 아니라 하단 바에서 빼고 여기에서 엽니다.
        item {
            OutlinedButton(
                onClick = onOpenStats,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(statsButtonTag)
            ) {
                Text("통계 보기")
            }
        }

        item {
            OutlinedButton(
                onClick = onOpenHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(historyButtonTag)
            ) {
                Text("노출 이력 보기")
            }
        }
    }
}

@Composable
private fun SyncConfirmDialog(
    action: PendingSyncAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val title: String
    val message: String
    val confirmLabel: String
    when (action) {
        PendingSyncAction.Merge -> {
            title = "서버와 병합할까요?"
            message = "양쪽 변경을 항목 단위로 합칩니다. 같은 항목은 나중에 고친 쪽이 남고, " +
                "지운 항목은 지워진 상태를 유지합니다. 합치기 직전 기기 데이터는 백업으로 저장됩니다."
            confirmLabel = "병합"
        }

        PendingSyncAction.Upload -> {
            title = "서버로 업로드할까요?"
            message = "서버의 기존 데이터가 이 기기의 데이터로 완전히 교체됩니다. " +
                "교체 직전 서버 데이터는 이 기기에 백업으로 저장됩니다."
            confirmLabel = "업로드"
        }

        PendingSyncAction.Download -> {
            title = "서버에서 가져올까요?"
            message = "이 기기의 기존 데이터가 서버 데이터로 완전히 교체됩니다. " +
                "교체 직전 기기 데이터는 백업으로 저장됩니다."
            confirmLabel = "가져오기"
        }

        is PendingSyncAction.Restore -> {
            title = "백업을 복원할까요?"
            message = "이 기기의 기존 데이터가 ${formatDateTime(action.backup.createdAt)}에 저장한 " +
                "'${action.backup.kind.label}' 백업으로 완전히 교체됩니다. " +
                "교체 직전 기기 데이터는 백업으로 저장됩니다."
            confirmLabel = "복원"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
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

private fun formatDateTime(millis: Long): String = LocalDateTime
    .ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"))

private fun formatBackupSize(sizeBytes: Long): String = when {
    sizeBytes >= 1024 -> "${(sizeBytes + 1023) / 1024}KB"
    else -> "${sizeBytes}B"
}

private fun defaultExportFileName(): String {
    val timestamp = LocalDateTime.now()
        .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
    return "app-good-words-export-$timestamp.json"
}
