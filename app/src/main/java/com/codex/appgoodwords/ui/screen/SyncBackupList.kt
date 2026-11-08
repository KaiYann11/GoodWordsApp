package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.SyncBackup
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 설정 카드에 보이는 백업 수. 나머지는 "백업 모두 보기"에서 봅니다(사용자가 정함, 2026-11-08). */
internal const val SETTINGS_BACKUP_PREVIEW = 3

internal const val settingsAllBackupsTag = "settings_all_backups"
internal const val syncBackupsListTag = "sync_backups_list"
internal const val backupConfirmTag = "backup_confirm"

internal fun backupRestoreTag(fileName: String) = "backup_restore_$fileName"

internal fun backupDeleteTag(fileName: String) = "backup_delete_$fileName"

/** 백업 한 줄에서 고르는 일. 둘 다 되돌리기 어려워 한 번 더 묻습니다. */
internal sealed interface BackupAction {
    val backup: SyncBackup

    data class Restore(override val backup: SyncBackup) : BackupAction
    data class Delete(override val backup: SyncBackup) : BackupAction
}

/**
 * 백업 한 줄 — 무엇의 백업인지, 언제·얼마나, 그리고 [복원][삭제].
 *
 * 설정 카드와 "모두 보기"가 같이 씁니다. 모두 보기는 종류별로 묶어 보여서 줄마다 종류를 다시 적지 않습니다([showKind]).
 */
@Composable
internal fun SyncBackupRow(
    backup: SyncBackup,
    onAction: (BackupAction) -> Unit,
    showKind: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (showKind) {
                Text(text = backup.kind.label, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = "${backupTime(backup.createdAt)} · ${backupSize(backup.sizeBytes)}",
                style = if (showKind) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
            )
        }
        TextButton(
            onClick = { onAction(BackupAction.Restore(backup)) },
            modifier = Modifier.testTag(backupRestoreTag(backup.fileName))
        ) {
            Text("복원")
        }
        TextButton(
            onClick = { onAction(BackupAction.Delete(backup)) },
            modifier = Modifier.testTag(backupDeleteTag(backup.fileName))
        ) {
            Text("삭제", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
internal fun BackupActionDialog(
    action: BackupAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val backup = action.backup
    val title: String
    val message: String
    val confirmLabel: String
    when (action) {
        is BackupAction.Restore -> {
            title = "백업을 복원할까요?"
            message = "이 기기의 기존 데이터가 ${backupTime(backup.createdAt)}에 저장한 " +
                "'${backup.kind.label}' 백업으로 완전히 교체됩니다. " +
                "교체 직전 기기 데이터는 백업으로 저장됩니다."
            confirmLabel = "복원"
        }

        is BackupAction.Delete -> {
            title = "백업을 지울까요?"
            message = "${backupTime(backup.createdAt)}에 저장한 '${backup.kind.label}' 백업 파일을 지웁니다. " +
                "지운 백업은 되살릴 수 없습니다. 기기와 서버의 데이터는 그대로입니다."
            confirmLabel = "삭제"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag(backupConfirmTag)) {
                Text(
                    text = confirmLabel,
                    color = if (action is BackupAction.Delete) MaterialTheme.colorScheme.error else Color.Unspecified
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

/**
 * 동기화 백업 모두 보기. 설정 카드에는 최근 [SETTINGS_BACKUP_PREVIEW]개만 두고, 나머지는 여기서 종류별로 봅니다
 * (사용자가 정함, 2026-11-08). 종류마다 5개씩 남아 많으면 25개라, 설정 카드에 다 두면 아래로 끝없이 길어졌습니다.
 *
 * 묶음은 가장 최근 백업이 있는 종류부터입니다 — 방금 일어난 일의 백업을 찾으러 오는 일이 가장 많습니다.
 */
@Composable
fun SyncBackupsScreen(
    backups: List<SyncBackup>,
    directory: String,
    onRestoreBackup: (SyncBackup) -> Unit,
    onDeleteBackup: (SyncBackup) -> Unit,
    modifier: Modifier = Modifier
) {
    // 화면이 다시 만들어지면 확인 대화상자를 닫아 실수로 실행되지 않게 합니다(설정과 같게).
    var pending by remember { mutableStateOf<BackupAction?>(null) }
    // 목록이 이미 최근 것부터라, 처음 나온 차례대로 묶으면 됩니다.
    val groups = remember(backups) { backups.groupBy { it.kind }.toList() }

    pending?.let { action ->
        BackupActionDialog(
            action = action,
            onDismiss = { pending = null },
            onConfirm = {
                pending = null
                when (action) {
                    is BackupAction.Restore -> onRestoreBackup(action.backup)
                    is BackupAction.Delete -> onDeleteBackup(action.backup)
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(syncBackupsListTag),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "intro") {
            Text(
                text = if (backups.isEmpty()) {
                    "아직 저장된 백업이 없습니다."
                } else {
                    "백업 ${backups.size}개. 병합·업로드·가져오기·복원 직전 상태이고, 종류별로 최근 5개까지 보관합니다."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        groups.forEach { (kind, rows) ->
            item(key = kind.name) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("${kind.label} ${rows.size}개", style = MaterialTheme.typography.titleSmall)
                        rows.forEach { backup ->
                            SyncBackupRow(backup = backup, onAction = { pending = it }, showKind = false)
                        }
                    }
                }
            }
        }
        if (directory.isNotBlank()) {
            item(key = "directory") {
                Text(
                    text = "저장 위치: $directory",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun backupTime(millis: Long): String = LocalDateTime
    .ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"))

/** 백업은 대개 몇 MB라 KB로 적으면 네 자리가 됩니다. */
internal fun backupSize(sizeBytes: Long): String = when {
    sizeBytes >= 1024L * 1024L -> String.format(Locale.ROOT, "%.1fMB", sizeBytes / (1024.0 * 1024.0))
    sizeBytes >= 1024 -> "${(sizeBytes + 1023) / 1024}KB"
    else -> "${sizeBytes}B"
}
