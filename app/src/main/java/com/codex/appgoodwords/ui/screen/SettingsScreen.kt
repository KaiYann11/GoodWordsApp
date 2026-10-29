package com.codex.appgoodwords.ui.screen

import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.AiFeedbackSettings
import com.codex.appgoodwords.data.AppUpdateState
import com.codex.appgoodwords.data.AiProvider
import com.codex.appgoodwords.data.DailyStep
import com.codex.appgoodwords.data.GrowthCadence
import com.codex.appgoodwords.data.ReminderSettings
import com.codex.appgoodwords.data.ReportPeriod
import com.codex.appgoodwords.data.ServerSyncSettings
import com.codex.appgoodwords.data.SyncBackup
import com.codex.appgoodwords.data.SyncStatus
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** 설정 화면에는 스위치가 여럿이라 테스트에서 자동 동기화 스위치만 집으려면 표식이 필요하다. */
internal const val autoSyncSwitchTag = "auto_sync_switch"

/** 이력은 하단 바에서 빠졌으므로 여기로 들어가는 길이 있는지 확인해야 한다. */
internal const val historyButtonTag = "history_button"

/** 교체와 병합은 결과가 정반대라 버튼을 헷갈리면 안 된다. */
internal const val fileMergeButtonTag = "file_merge_button"

/** 글자마다 저장하는 칸들. 치는 동안 늦게 돌아온 저장값이 칸을 덮지 않는지 짚습니다. */
internal const val reminderIntervalFieldTag = "reminder_interval_field"
internal const val serverUrlFieldTag = "server_url_field"
internal const val serverApiKeyFieldTag = "server_api_key_field"

/** AI 열쇠 칸과 주기 칩. 설정에는 칸과 칩이 여럿이라 표식으로 짚습니다. */
internal const val aiApiKeyFieldTag = "ai_api_key_field"

/** 오늘의 한마디 스위치와, 무엇이 나가는지 보는 자리. */
internal const val aiCoachSwitchTag = "ai_coach_switch"
internal const val aiCoachPreviewTag = "ai_coach_preview"
internal const val aiCoachNudgeSwitchTag = "ai_coach_nudge_switch"

/** 하루 횟수와 묻는 주기. 칩이 여럿이라 값으로 짚습니다. */
internal fun aiCoachLimitTag(limit: Int): String = "ai_coach_limit_$limit"
internal fun aiCoachGapTag(minutes: Int): String = "ai_coach_gap_$minutes"

/** 앱을 닫아도 받는 스위치와, 하루 몫이 금방 바닥난다는 안내. */
internal const val aiCoachBackgroundSwitchTag = "ai_coach_background_switch"
/** 정확한 알람 권한이 없을 때 그 설정 화면으로 데려가는 단추. */
internal const val aiCoachExactAlarmTag = "ai_coach_exact_alarm"
/** 목표 탭에 적은 것을 AI에 함께 보낼지. */
internal const val aiIncludeGoalsSwitchTag = "ai_include_goals_switch"
internal const val aiCoachShortDayTag = "ai_coach_short_day"

/** 이보다 짧은 동안 하루 몫을 다 쓰면 알려 줍니다(6시간). 그 뒤 하루가 길게 조용합니다. */
private const val COACH_SHORT_DAY_MINUTES = 6 * 60

internal fun aiScheduleTag(name: String): String = "ai_schedule_" + name.ifBlank { "off" }

internal fun aiProviderTag(provider: AiProvider): String = "ai_provider_" + provider.name.lowercase()

/** 일기 잠금 스위치. 잠금 칸에 스위치가 둘이라 이름으로 짚습니다. */
internal const val diaryLockSwitchTag = "diary_lock_switch"

/** 오늘의 걸음 줄. 네 줄이 같은 모양이라 이름으로 짚습니다. */
internal fun dailyStepRowTag(step: DailyStep): String = "daily_step_" + step.name.lowercase()

internal fun dailyStepToggleTag(step: DailyStep): String = "daily_step_toggle_" + step.name.lowercase()

internal fun dailyStepUpTag(step: DailyStep): String = "daily_step_up_" + step.name.lowercase()

/**
 * 설정의 갈래. 첫 화면은 이 목록만 늘어놓고, 누르면 그 갈래 화면이 열립니다.
 *
 * 한 화면에 카드 열다섯 개를 이어 두었더니 알림과 데이터와 서버가 뒤섞여 어디에 무엇이 있는지 눈에 들어오지
 * 않았습니다(사용자 말, 2026-10-19). 휴대폰 기본 설정 앱처럼 갈래로 나눴습니다. 자주 고치는 것이 앞입니다.
 */
enum class SettingsSection(val title: String, val icon: ImageVector) {
    NOTIFICATIONS("알림", Icons.Outlined.Notifications),
    AI("AI", Icons.Outlined.AutoAwesome),
    STEPS("오늘의 걸음", Icons.Outlined.Flag),
    SYNC("서버·동기화", Icons.Outlined.Sync),
    DATA("데이터·보관함", Icons.Outlined.Storage),
    LOCK("잠금", Icons.Outlined.Lock)
}

/** 목록의 갈래 줄과, 설정 화면 전체 목록(굴려서 찾을 때 짚습니다 — 안에 가로로 굴리는 칩 줄이 여럿 있습니다). */
internal fun settingsSectionTag(section: SettingsSection): String = "settings_section_" + section.name.lowercase()
internal const val settingsListTag = "settings_list"

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
    onOpenHistory: () -> Unit = {},
    appLockEnabled: Boolean = false,
    onAppLockChanged: (Boolean) -> Unit = {},
    /** 일기만 따로 거는 잠금. 앱 잠금과 별개입니다. */
    diaryLockEnabled: Boolean = false,
    onDiaryLockChanged: (Boolean) -> Unit = {},
    aiFeedbackSettings: AiFeedbackSettings = AiFeedbackSettings(),
    onAiFeedbackSettingsChanged: (AiFeedbackSettings) -> Unit = {},
    /**
     * 오늘의 한마디로 무엇이 나가는지 보여 줍니다.
     *
     * 저절로 하루에 여러 번 나가는 길이라, 켜기 전에 눈으로 확인할 수 있어야 합니다.
     * 실제로 보내는 글과 **같은 함수**로 만듭니다([DailyCoachPrompt.chatPrompt]).
     */
    onPreviewCoachPrompt: (suspend () -> Result<String>)? = null,
    /**
     * 정확한 알람을 걸 수 있는지. 없으면 배경 한마디가 정한 때보다 늦게 올 수 있어 한마디 칸에서 알리고
     * 그 권한 화면으로 데려갑니다([onOpenExactAlarmSettings]). 할 일 알람과 같은 권한입니다.
     */
    canScheduleExactAlarms: Boolean = true,
    onOpenExactAlarmSettings: () -> Unit = {},
    /** 하루의 축으로 삼을 걸음. 고른 차례가 그대로 홈 카드의 차례입니다. */
    dailySteps: List<DailyStep> = DailyStep.DEFAULTS,
    onDailyStepsChanged: (List<DailyStep>) -> Unit = {},
    /** 지금 깔려 있는 버전. 화면이 BuildConfig를 직접 보지 않아야 시험에서 값을 정해 줄 수 있습니다. */
    currentVersionName: String = "",
    currentVersionCode: Int = 0,
    appUpdateState: AppUpdateState = AppUpdateState.Idle,
    onCheckAppUpdate: () -> Unit = {},
    onDownloadAppUpdate: () -> Unit = {},
    onInstallAppUpdate: (String) -> Unit = {},
    onDismissAppUpdate: () -> Unit = {},
    /**
     * 보고 있는 갈래. null이면 갈래 목록입니다.
     *
     * 밖(`AppGoodWordsApp`)에서 들고 있습니다. 위쪽 막대가 갈래 이름을 제목으로 띄우고, 막대의 ←가 설정을 닫기 전에
     * 목록으로 먼저 돌아가야 하기 때문입니다. 화면 안에 돌아가기 단추를 두면 내용과 함께 굴러 올라가 사라집니다.
     */
    section: SettingsSection? = null,
    onSectionChange: (SettingsSection?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // 갈래 화면에서 휴대폰의 뒤로 가기를 누르면 설정을 닫지 않고 목록으로 돌아옵니다(위쪽 막대의 ←와 같게).
    BackHandler(enabled = section != null) { onSectionChange(null) }
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

    // 세 칸은 글자마다 저장하고, 저장된 값은 한 박자 늦게 돌아옵니다. 치는 동안 돌아온 옛 값으로 칸을 덮으면
    // 그 사이 친 글자가 지워지고 커서가 엉뚱한 데로 가, 빠르게 친 서버 주소가 뒤섞인 채 저장됐습니다
    // (에뮬레이터, 2026-10-25). 반복 간격은 "3"을 치자마자 최소값 15로 덮여 "30"을 칠 수 없었습니다.
    // 그래서 칸을 누르고 있는 동안은 칸이 주인이고, 칸을 떠나면 저장된 값을 보여 줍니다.
    var intervalFieldFocused by remember { mutableStateOf(false) }
    var serverUrlFieldFocused by remember { mutableStateOf(false) }
    var serverApiKeyFieldFocused by remember { mutableStateOf(false) }

    LaunchedEffect(settings.intervalMinutes, intervalFieldFocused) {
        val normalized = settings.intervalMinutes.toString()
        if (!intervalFieldFocused && intervalMinutesText != normalized) {
            intervalMinutesText = normalized
        }
    }

    LaunchedEffect(serverSyncSettings.serverUrl, serverUrlFieldFocused) {
        if (!serverUrlFieldFocused && serverUrlText != serverSyncSettings.serverUrl) {
            serverUrlText = serverSyncSettings.serverUrl
        }
    }

    LaunchedEffect(serverSyncSettings.apiKey, serverApiKeyFieldFocused) {
        if (!serverApiKeyFieldFocused && serverApiKeyText != serverSyncSettings.apiKey) {
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
        modifier = modifier
            .fillMaxSize()
            .testTag(settingsListTag),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val current = section
        if (current == null) {
            // 첫 화면은 갈래만 늘어놓습니다. 갈래마다 지금 상태를 한 줄로 적어, 들어가 보지 않고도 무엇이 켜져 있는지 압니다.
            items(SettingsSection.entries) { entry ->
                SettingsSectionRow(
                    section = entry,
                    summary = when (entry) {
                        SettingsSection.NOTIFICATIONS -> notificationsSummary(settings)
                        SettingsSection.AI -> aiSummary(aiFeedbackSettings)
                        SettingsSection.STEPS -> dailySteps.joinToString(" · ") { it.label }
                        SettingsSection.SYNC -> syncSummary(serverSyncSettings)
                        SettingsSection.DATA -> "백업·가져오기 · 카테고리 ${categories.size}개 · 노출 이력"
                        SettingsSection.LOCK -> lockSummary(appLockEnabled, diaryLockEnabled)
                    },
                    // 켜 두었는데 돌지 않는 것은 목록에서부터 보여야 합니다. 들어가 봐야 아는 실패는 없는 것과 같습니다.
                    warning = when (entry) {
                        SettingsSection.NOTIFICATIONS ->
                            "알림 권한이 꺼져 있어 알림이 오지 않습니다".takeIf { settings.remindersEnabled && notificationsBlocked }
                        SettingsSection.SYNC -> "마지막 동기화가 실패했습니다".takeIf { syncStatus.failed }
                        else -> null
                    },
                    onClick = { onSectionChange(entry) }
                )
            }
        } else {
            // 어느 갈래인지는 위쪽 막대의 제목이 말합니다.
            when (current) {
                SettingsSection.NOTIFICATIONS -> {
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
                                // 홈 맨 위의 글귀를 앱을 열 때마다 새것으로 넘길지. 끄면 위젯·알림이 마지막으로 보여 준 것이 남습니다.
                                SettingSwitchRow(
                                    title = "앱을 열 때마다 새 글귀로",
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
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { intervalFieldFocused = it.isFocused }
                                        .testTag(reminderIntervalFieldTag),
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
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("알림에 보낼 카테고리", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "주기 알림과 앱을 열 때 보여 줄 글귀를 이 카테고리에서만 고릅니다.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                        Button(
                            onClick = onSendTestNotification,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("테스트 알림 보내기")
                        }
                    }
                }
                SettingsSection.AI -> {
                    item {
                        AiFeedbackSection(
                            settings = aiFeedbackSettings,
                            onChanged = onAiFeedbackSettingsChanged,
                            onPreviewCoachPrompt = onPreviewCoachPrompt,
                            canScheduleExactAlarms = canScheduleExactAlarms,
                            onOpenExactAlarmSettings = onOpenExactAlarmSettings
                        )
                    }
                }
                SettingsSection.STEPS -> {
                    item {
                        DailyStepsSection(steps = dailySteps, onChanged = onDailyStepsChanged)
                    }
                }
                SettingsSection.SYNC -> {
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
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { serverUrlFieldFocused = it.isFocused }
                                        .testTag(serverUrlFieldTag),
                                    label = { Text("서버 주소") },
                                    supportingText = {
                                        Text("예: https://서버이름.ts.net · 인증서가 확인되는 HTTPS 주소를 입력해 주세요.")
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
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { serverApiKeyFieldFocused = it.isFocused }
                                        .testTag(serverApiKeyFieldTag),
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
                        AppUpdateCard(
                            state = appUpdateState,
                            currentVersionName = currentVersionName,
                            currentVersionCode = currentVersionCode,
                            serverConfigured = serverUrlText.isNotBlank(),
                            onCheck = onCheckAppUpdate,
                            onDownload = onDownloadAppUpdate,
                            onInstall = onInstallAppUpdate,
                            onDismiss = onDismissAppUpdate
                        )
                    }
                }
                SettingsSection.DATA -> {
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
                    // 이력은 매일 볼 화면이 아니라 하단 바에서 빼고 여기에서 엽니다. 통계는 홈이 맡습니다.
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
                SettingsSection.LOCK -> {
                    item {
                        AppLockSection(
                            enabled = appLockEnabled,
                            onChanged = onAppLockChanged,
                            diaryLocked = diaryLockEnabled,
                            onDiaryLockChanged = onDiaryLockChanged
                        )
                    }
                }
            }
        }
    }
}

/**
 * 목록의 갈래 한 줄. 아이콘·이름·지금 상태 한 줄·(있으면) 경고.
 *
 * 아이콘 바탕은 모든 갈래가 같은 파랑입니다. 색을 갈래마다 달리하면 이 화면만 다른 앱처럼 보입니다(`Color.kt`).
 * 대신 이름을 굵게, 상태는 한 줄로 줄여 눈이 이름부터 짚게 합니다.
 */
@Composable
private fun SettingsSectionRow(
    section: SettingsSection,
    summary: String,
    warning: String?,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
            .testTag(settingsSectionTag(section)),
        shape = shape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(section.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (warning != null) {
                    Text(warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun notificationsSummary(settings: ReminderSettings): String = buildList {
    add(if (settings.remindersEnabled) "주기 알림 ${formatInterval(settings.intervalMinutes)}마다" else "주기 알림 꺼짐")
    if (settings.dailySummaryEnabled) add("일일 집계 ${formatTime(settings.summaryHour, settings.summaryMinute)}")
}.joinToString(" · ")

private fun aiSummary(settings: AiFeedbackSettings): String = buildList {
    add(settings.activeProvider.label)
    add(if (settings.coachEnabled) "한마디 ${formatInterval(settings.gapMinutes.toInt())}마다" else "한마디 꺼짐")
    settings.scheduledPeriod?.let { add("돌아보기 ${it.label}") }
}.joinToString(" · ")

/** 서버는 주소 전체 대신 이름 첫 마디만 적습니다(`https://맥미니.….ts.net` → `맥미니`). 한 줄에 다 들어가야 읽힙니다. */
private fun syncSummary(settings: ServerSyncSettings): String {
    if (settings.serverUrl.isBlank()) return "서버 없이 이 기기만"
    val host = settings.serverUrl.substringAfter("://").substringBefore('/').substringBefore('.')
    return buildList {
        add(host)
        add(if (settings.autoSyncEnabled) "자동 ${settings.effectiveIntervalHours}시간마다" else "자동 동기화 꺼짐")
    }.joinToString(" · ")
}

private fun lockSummary(appLock: Boolean, diaryLock: Boolean): String = buildList {
    add(if (appLock) "앱 잠금 켜짐" else "앱 잠금 꺼짐")
    if (diaryLock) add("일기 잠금 켜짐")
}.joinToString(" · ")

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
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier
        )
    }
}

private fun formatTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

private fun formatInterval(intervalMinutes: Int): String {
    return when {
        intervalMinutes == 1440 -> "하루"
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

/**
 * 하루의 축으로 삼을 걸음을 고릅니다.
 *
 * 예전에는 글귀·루틴·일기 셋으로 박혀 있었습니다. 글귀로 하루를 여는 사람도 있지만
 * 할 일을 끝내는 것이 하루인 사람도 있는데, 그런 사람에게는 첫 칸이 늘 남의 것이었습니다.
 *
 * **연속 날수는 어디에도 적혀 있지 않고 기록에서 다시 셉니다.** 그래서 축을 바꾸면 지난
 * 날수도 곧바로 새 기준이 됩니다. 그 사실을 화면에 적어 두어야, 어제 12일이던 것이
 * 오늘 8일로 보일 때 고장으로 읽히지 않습니다.
 */
@Composable
private fun DailyStepsSection(
    steps: List<DailyStep>,
    onChanged: (List<DailyStep>) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("오늘의 걸음", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "홈 카드에 놓을 걸음과 그 차례를 고릅니다. 고른 걸음을 모두 밟은 날만 " +
                    "이어 온 날로 셉니다. 바꾸면 지난 날수도 새 기준으로 다시 셉니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            DailyStep.entries.forEach { step ->
                val position = steps.indexOf(step)
                val chosen = position >= 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(dailyStepRowTag(step)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = chosen,
                        // 하나도 안 남기면 카드가 뜻을 잃습니다. 마지막 하나는 끄지 못하게 둡니다.
                        enabled = !chosen || steps.size > 1,
                        onCheckedChange = { on ->
                            onChanged(if (on) steps + step else steps - step)
                        },
                        modifier = Modifier.testTag(dailyStepToggleTag(step))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(step.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = step.hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (chosen) {
                        IconButton(
                            onClick = { onChanged(steps.moved(position, position - 1)) },
                            enabled = position > 0,
                            modifier = Modifier.testTag(dailyStepUpTag(step))
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.KeyboardArrowUp,
                                contentDescription = "${step.label}을 앞으로"
                            )
                        }
                        IconButton(
                            onClick = { onChanged(steps.moved(position, position + 1)) },
                            enabled = position < steps.lastIndex
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.KeyboardArrowDown,
                                contentDescription = "${step.label}을 뒤로"
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 한 자리 옮깁니다. 줄 밖을 가리키면 그대로 둡니다. */
private fun List<DailyStep>.moved(from: Int, to: Int): List<DailyStep> {
    if (from !in indices || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/**
 * AI 성장 피드백 설정.
 *
 * 열쇠는 이 기기에만 둡니다. 동기화 스냅샷과 백업 파일에는 넣지 않으므로, 기기를 새로
 * 붙이면 거기서 다시 넣거나 서버에 넣어 두어야 합니다.
 */
@Composable
private fun AiFeedbackSection(
    settings: AiFeedbackSettings,
    onChanged: (AiFeedbackSettings) -> Unit,
    onPreviewCoachPrompt: (suspend () -> Result<String>)? = null,
    canScheduleExactAlarms: Boolean = true,
    onOpenExactAlarmSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val provider = settings.activeProvider
    var apiKeyText by rememberSaveable { mutableStateOf(settings.activeKey) }
    val coroutineScope = rememberCoroutineScope()
    var coachPreview by remember { mutableStateOf<String?>(null) }

    // 공급자를 바꾸면 칸에 남아 있던 것이 다른 곳의 열쇠입니다. 그대로 저장하면 서로 뒤바뀝니다.
    LaunchedEffect(settings.activeKey, provider) {
        if (apiKeyText != settings.activeKey) apiKeyText = settings.activeKey
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("AI 돌아보기", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "기록을 AI에게 보내 잘한 점과 다음 걸음을 받아 옵니다. " +
                    "서버 주소가 설정되어 있으면 서버를 먼저 쓰고, 없으면 아래 열쇠로 이 기기가 직접 부릅니다. " +
                    "Claude CLI·Codex CLI는 서버에 로그인해 둔 CLI가, Ollama는 서버 PC에서 도는 모델이 열쇠 없이 답합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("어디에 물어볼지", style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AiProvider.entries) { choice ->
                    FilterChip(
                        selected = provider == choice,
                        onClick = { onChanged(settings.withProvider(choice)) },
                        label = { Text(choice.label) },
                        modifier = Modifier.testTag(aiProviderTag(choice))
                    )
                }
            }

            if (provider.serverOnly) {
                // 열쇠 칸을 두면 무엇을 넣어야 하나 헤맵니다. 넣을 것이 없습니다.
                val who = if (provider == AiProvider.OLLAMA) {
                    // PC 한 대가 쓰는 것이라 클라우드보다 느립니다. 미리 말해 두지 않으면 멈춘 줄 압니다.
                    "서버 PC에서 도는 Ollama가 답합니다. 기록이 그 PC 밖으로 나가지 않고, 모델은 서버가 고릅니다. " +
                        "PC 성능에 따라 답이 오는 데 1~2분 걸릴 수 있습니다."
                } else {
                    "서버에 설치되어 로그인된 ${provider.label}가 답합니다."
                }
                Text(
                    text = "$who 열쇠는 필요 없고, " +
                        "위의 서버 주소가 있어야 합니다. 서버가 꺼져 있으면 이 기기에서 대신 부를 길은 없습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                OutlinedTextField(
                    value = apiKeyText,
                    onValueChange = { apiKeyText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(aiApiKeyFieldTag),
                    label = { Text("${provider.label} API 키") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    supportingText = {
                        Text("${provider.keyHint}. 이 기기에만 저장됩니다. 백업·동기화에는 담기지 않습니다.")
                    }
                )
                OutlinedButton(
                    onClick = { onChanged(settings.withKey(apiKeyText)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("열쇠 저장")
                }
            }

            Text("모델", style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(provider.models) { model ->
                    FilterChip(
                        selected = settings.effectiveModel == model,
                        onClick = { onChanged(settings.copy(model = model)) },
                        label = { Text(provider.modelLabel(model)) }
                    )
                }
            }

            Text("자동 실행 주기", style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = !settings.isScheduled,
                        onClick = { onChanged(settings.copy(schedule = "")) },
                        label = { Text("꺼짐") },
                        modifier = Modifier.testTag(aiScheduleTag(""))
                    )
                }
                items(ReportPeriod.entries.filter { it != ReportPeriod.MANUAL }) { period ->
                    FilterChip(
                        selected = settings.schedule == period.name,
                        onClick = { onChanged(settings.copy(schedule = period.name)) },
                        label = { Text(period.label) },
                        modifier = Modifier.testTag(aiScheduleTag(period.name))
                    )
                }
            }

            if (settings.isScheduled) {
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute -> onChanged(settings.copy(hour = hour, minute = minute)) },
                            settings.hour,
                            settings.minute,
                            true
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("실행 시각 %02d:%02d".format(settings.hour, settings.minute))
                }
                Text(
                    text = "그날이 되면 이 시각에 만들고 알림으로 알려 드립니다. " +
                        "폰이 꺼져 있었으면 다음에 깨어날 때 만듭니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingSwitchRow(
                title = "일기 본문도 보내기",
                checked = settings.includeDiaryBody,
                onCheckedChange = { onChanged(settings.copy(includeDiaryBody = it)) }
            )
            Text(
                text = if (settings.includeDiaryBody) {
                    "일기 본문이 그대로 AI에 전달됩니다. 피드백은 구체적이지만, 가장 사적인 글이 밖으로 나갑니다."
                } else {
                    "일기는 날짜·기분·날씨·글자 수만 보냅니다. 본문은 이 기기를 떠나지 않습니다."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 일기 본문과 같은 결의 스위치입니다. 되고 싶은 모습은 사적인 글이라 내보낼지는 사용자가 정합니다.
            SettingSwitchRow(
                title = "목표·이상향·롤모델도 보내기",
                checked = settings.includeGoals,
                onCheckedChange = { onChanged(settings.copy(includeGoals = it)) },
                modifier = Modifier.testTag(aiIncludeGoalsSwitchTag)
            )
            Text(
                text = if (settings.includeGoals) {
                    "목표 탭에 적은 것의 제목·설명·이어 둔 루틴 이름이 함께 나가, 돌아보기와 한마디가 오늘 한 것을 " +
                        "그 방향과 이어 봅니다. 되새긴 기록은 보내지 않습니다."
                } else {
                    "목표 탭에 적은 것은 보내지 않습니다. 켜면 돌아보기와 한마디가 그 방향을 알고 말합니다."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 돌아보기와 따로 켭니다. 한 달에 한 번 돌아보면서도 오늘은 곁에 두고 싶을 수
            // 있고, 그 반대도 마찬가지입니다.
            SettingSwitchRow(
                title = "오늘의 한마디 받기",
                checked = settings.coachEnabled,
                onCheckedChange = { onChanged(settings.copy(coachEnabled = it)) },
                modifier = Modifier.testTag(aiCoachSwitchTag)
            )
            Text(
                text = if (settings.coachEnabled) {
                    "홈에서, 오늘 한 것을 보고 한마디를 건넵니다. " +
                        "정한 주기마다 새로 물어보고, 한마디에 답하면 그 말에 이어 답합니다. " +
                        "말을 주고받으면 다음 한마디는 그때부터 한 주기 뒤에 오고, 나눈 말을 알고 씁니다."
                } else {
                    "오늘 한 것을 보고 한마디·글귀·해 볼 일을 받습니다. " +
                        "물을 때마다 값이 들어서 켜는 것은 직접 정합니다."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (settings.coachEnabled) {
                // 값이 드는 정도는 사람마다 다르게 느낍니다. 앱이 정해 주지 않고 고르게 합니다.
                Text("하루에 저절로 묻는 횟수", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AiFeedbackSettings.ASK_LIMIT_CHOICES) { limit ->
                        FilterChip(
                            selected = settings.askLimit == limit,
                            onClick = { onChanged(settings.copy(coachAskLimit = limit)) },
                            label = { Text(if (limit == 0) "안 함" else "${limit}번") },
                            modifier = Modifier.testTag(aiCoachLimitTag(limit))
                        )
                    }
                }

                if (settings.asksOnlyWhenAsked) {
                    Text(
                        text = "저절로는 묻지 않습니다. 카드의 '받아 보기'를 누를 때만 물어봅니다. " +
                            "아래 루틴 알림은 AI를 부르지 않아 그대로 뜹니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text("묻는 주기", style = MaterialTheme.typography.titleSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(AiFeedbackSettings.GAP_MINUTE_CHOICES) { minutes ->
                            FilterChip(
                                selected = settings.gapMinutes.toInt() == minutes,
                                onClick = { onChanged(settings.copy(coachGapMinutes = minutes)) },
                                label = { Text(formatInterval(minutes)) },
                                modifier = Modifier.testTag(aiCoachGapTag(minutes))
                            )
                        }
                    }
                    Text(
                        text = "${formatInterval(settings.gapMinutes.toInt())}마다, 하루 ${settings.askLimit}번까지 " +
                            "물어봅니다. 오늘 한 것이 하나도 없으면 묻지 않습니다. " +
                            "직접 누르는 것은 여기 세지 않습니다. 실패한 것도 한 번으로 셉니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // 주기가 짧고 횟수가 적으면 아침에 하루 몫을 다 쓰고 남은 하루는 조용합니다. 고른 대로
                    // 두되, 그 결과를 숫자로 보여 줍니다.
                    val spanMinutes = (settings.askLimit - 1) * settings.gapMinutes.toInt()
                    if (settings.askLimit > 1 && spanMinutes < COACH_SHORT_DAY_MINUTES) {
                        Text(
                            text = "이대로면 첫 한마디부터 ${formatInterval(spanMinutes)} 안에 하루 ${settings.askLimit}번을 " +
                                "다 씁니다. 하루 내내 받으려면 주기를 늘리거나 횟수를 늘려 보세요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag(aiCoachShortDayTag)
                        )
                    }

                    // 앱을 열지 않는 날에도 한마디가 오게 합니다. 값이 드는 일이라 스위치로 보이게 둡니다.
                    SettingSwitchRow(
                        title = "앱을 닫아도 받아 알림으로",
                        checked = settings.coachBackground,
                        onCheckedChange = { onChanged(settings.copy(coachBackground = it)) },
                        modifier = Modifier.testTag(aiCoachBackgroundSwitchTag)
                    )
                    Text(
                        text = if (settings.coachBackground) {
                            "앱을 닫아 두어도 정한 주기가 되면 알람처럼 깨워 받아 알림으로 띄웁니다. " +
                                "앱을 보고 있을 때는 알림 대신 홈의 한마디 칸에 점이 뜹니다."
                        } else {
                            "앱을 열어 둔 동안에만 묻습니다."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // 권한이 없어도 알람은 걸립니다(잠자기 중에도 울리되 몇 분 밀릴 수 있는 것으로). 막지 않고 알리기만 합니다.
                    if (settings.coachBackground && !canScheduleExactAlarms) {
                        Text(
                            text = "정확한 알람 권한이 꺼져 있어 정한 때보다 몇 분 늦게 올 수 있습니다. " +
                                "할 일 알람과 같은 권한입니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(
                            onClick = onOpenExactAlarmSettings,
                            modifier = Modifier.testTag(aiCoachExactAlarmTag)
                        ) {
                            Text("알람 권한 켜기")
                        }
                    }
                }

                // 무거운 쪽(AI)과 따로 켭니다. 이쪽은 값이 들지 않습니다.
                SettingSwitchRow(
                    title = "아직 안 밟은 루틴 알려 주기",
                    checked = settings.coachNudgeEnabled,
                    onCheckedChange = { onChanged(settings.copy(coachNudgeEnabled = it)) },
                    modifier = Modifier.testTag(aiCoachNudgeSwitchTag)
                )
                Text(
                    text = if (settings.coachNudgeEnabled) {
                        "이어 오던 루틴이 오늘 아직이면 그것부터 짚어 줍니다. " +
                            "AI를 부르지 않고 기기에서 세기 때문에 값이 들지 않고, 연결이 없어도 뜹니다. " +
                            "남은 개수를 세지 않고 다음 하나만 말합니다."
                    } else {
                        "루틴 줄을 두지 않습니다. 카드에는 AI가 써 준 것만 남습니다."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (onPreviewCoachPrompt != null) {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            coachPreview = onPreviewCoachPrompt()
                                .getOrElse { failure -> failure.message ?: "미리 보기를 만들지 못했습니다." }
                        }
                    },
                    modifier = Modifier.testTag(aiCoachPreviewTag)
                ) {
                    Text("무엇이 나가는지 보기")
                }
            }

            SettingSwitchRow(
                title = "오래 안 하면 알리기",
                checked = settings.nudgeEnabled,
                onCheckedChange = { onChanged(settings.copy(nudgeEnabled = it)) }
            )
            Text(
                text = if (settings.nudgeEnabled) {
                    "${GrowthCadence.OVERDUE_DAYS}일 넘게 돌아보지 않으면 알립니다. " +
                        "알리기만 하고 아무것도 만들지 않아 값이 들지 않습니다."
                } else {
                    "뜸해져도 알리지 않습니다. 마지막으로 돌아본 날은 돌아보기 화면에 늘 적혀 있습니다."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (settings.lastRunAt > 0L) {
                Text(
                    text = "마지막 실행: ${formatDateTime(settings.lastRunAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (settings.lastError.isNotBlank()) {
                Text(
                    text = settings.lastError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    coachPreview?.let { text ->
        AlertDialog(
            onDismissRequest = { coachPreview = null },
            title = { Text("오늘의 한마디로 나가는 글") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(text = text, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { coachPreview = null }) { Text("닫기") }
            }
        )
    }
}

/**
 * 앱 잠금 설정.
 *
 * **기기에 잠금이 없으면 켤 수 없습니다.** 앱만 잠가 봐야 지킬 수 있는 것이 없고, 무엇보다
 * 풀 방법이 없어서 사용자가 자기 기록에서 갇힙니다.
 */
@Composable
private fun AppLockSection(
    enabled: Boolean,
    onChanged: (Boolean) -> Unit,
    diaryLocked: Boolean = false,
    onDiaryLockChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val canLock = remember { AppLock.canLock(context) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("앱 잠금", style = MaterialTheme.typography.titleMedium)
            SettingSwitchRow(
                title = "열 때 기기 잠금으로 확인",
                checked = enabled && canLock,
                onCheckedChange = { wanted -> if (canLock) onChanged(wanted) }
            )
            Text(
                text = when {
                    !canLock ->
                        "휴대폰에 화면 잠금(지문·PIN·패턴)이 없어 켤 수 없습니다. " +
                            "휴대폰 설정에서 화면 잠금을 먼저 정해 주세요."

                    enabled ->
                        "앱을 켤 때와 1분 넘게 자리를 비운 뒤 돌아올 때 확인합니다. " +
                            "사진을 고르러 잠깐 다녀오는 것으로는 잠기지 않습니다."

                    else ->
                        "일기처럼 사적인 기록을 남 눈에서 가립니다. 비밀번호를 따로 만들지 않고 " +
                            "휴대폰에 이미 있는 잠금을 그대로 씁니다."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (canLock) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                }
            )

            // 앱 잠금과 따로 둡니다. 앱은 열어 두고 쓰면서도 일기는 가리고 싶을 수 있습니다.
            SettingSwitchRow(
                title = "일기를 열 때 따로 확인",
                checked = diaryLocked && canLock,
                onCheckedChange = { wanted -> if (canLock) onDiaryLockChanged(wanted) },
                modifier = Modifier.testTag(diaryLockSwitchTag)
            )
            Text(
                text = if (diaryLocked) {
                    "일기 본문을 펼칠 때 한 번 확인합니다. 날짜와 기분은 그대로 보입니다. " +
                        "한 번 풀면 그 화면을 떠날 때까지 열어 둡니다."
                } else {
                    "폰을 잠깐 건네줄 때 나머지는 보여 줘도 일기는 아닐 수 있습니다."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
