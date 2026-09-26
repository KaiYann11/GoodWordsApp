package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.ContentDraft
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ContentType
import kotlinx.coroutines.launch

/**
 * 계속 확인할 기준 목록. 하지 않을 일과 원칙이 같은 화면을 씁니다.
 *
 * 둘은 방향만 반대이고(피할 것 / 지킬 것) 하는 일은 같습니다. 화면을 따로 두면 한쪽만 고쳐져
 * 어느 날부터 두 탭이 다르게 동작합니다. 실제로 지키기 어려운 것은 "지우기 실패를 어떻게
 * 알리는가" 같은 잔가지라, 그 잔가지를 한 벌만 두는 편이 낫습니다.
 *
 * **수행 버튼이 없습니다.** 기준은 밟는 것이 아니라 지키는 것이어서, 지킨 날을 세기 시작하면
 * 루틴이 하나 더 생길 뿐입니다([ContentType.PRINCIPLE]).
 */
@Composable
fun RuleListScreen(
    labels: RuleListLabels,
    items: List<ContentItemEntity>,
    onSave: suspend (ContentDraft) -> Result<Unit>,
    onDelete: suspend (Long) -> Result<Unit>,
    modifier: Modifier = Modifier,
    focusId: Long? = null
) {
    val records = items.filter { it.type == labels.type }.sortedByDescending { it.createdAt }
    var editingId by rememberSaveable(labels.tagPrefix) { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable(labels.tagPrefix) { mutableStateOf<Long?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember(deletingId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(focusId, records) {
        val index = records.indexOfFirst { it.id == focusId }
        if (index >= 0) listState.animateScrollToItem(index + 1)
    }

    if (editingId != null) {
        RuleEditorDialog(
            labels = labels,
            initialDraft = records.firstOrNull { it.id == editingId }?.let(ContentDraft::fromItem)
                ?: ContentDraft(type = labels.type),
            onSave = onSave,
            onDismiss = { editingId = null }
        )
    }
    records.firstOrNull { it.id == deletingId }?.let { item ->
        AlertDialog(
            onDismissRequest = { if (!deleting) deletingId = null },
            title = { Text(labels.deleteTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("'${item.title}' 항목을 삭제할까요?")
                    deleteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    modifier = Modifier.testTag("${labels.tagPrefix}_confirm_delete"),
                    onClick = {
                        deleting = true
                        scope.launch {
                            val result = onDelete(item.id)
                            deleting = false
                            if (result.isSuccess) deletingId = null
                            else deleteError = result.exceptionOrNull()?.message ?: "삭제하지 못했습니다. 다시 시도해 주세요."
                        }
                    }
                ) { Text(if (deleting) "삭제 중…" else "삭제") }
            },
            dismissButton = {
                TextButton(onClick = { deletingId = null }, enabled = !deleting) { Text("취소") }
            }
        )
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().testTag("${labels.tagPrefix}_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${labels.name} ${records.size}개", style = MaterialTheme.typography.titleLarge)
                Text(
                    labels.intro,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { editingId = 0L },
                    modifier = Modifier.fillMaxWidth().testTag("${labels.tagPrefix}_add")
                ) { Text("${labels.name} 추가") }
            }
        }
        if (records.isEmpty()) {
            item {
                EmptyCard(
                    title = labels.emptyTitle,
                    body = labels.emptyBody
                )
            }
        }
        items(records, key = { it.id }) { item ->
            Card(
                modifier = Modifier.fillMaxWidth().testTag("${labels.tagPrefix}_item_${item.id}"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    1.dp,
                    if (item.id == focusId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    if (item.body.isNotBlank()) Text(item.body, style = MaterialTheme.typography.bodyLarge)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = { editingId = item.id },
                            modifier = Modifier.testTag("${labels.tagPrefix}_edit_${item.id}")
                        ) { Text("수정") }
                        TextButton(
                            onClick = { deletingId = item.id },
                            modifier = Modifier.testTag("${labels.tagPrefix}_delete_${item.id}")
                        ) { Text("삭제") }
                    }
                }
            }
        }
    }
}

/**
 * 화면에 쓰는 말과 시험용 꼬리표.
 *
 * 종류마다 다른 것은 말뿐이라 한자리에 모읍니다. 꼬리표 앞머리(`tagPrefix`)는 바꾸지 마세요 —
 * 계측 시험이 이 이름으로 버튼을 찾습니다.
 */
data class RuleListLabels(
    val type: ContentType,
    val name: String,
    val intro: String,
    val titleLabel: String,
    val titlePlaceholder: String,
    val memoLabel: String,
    val memoPlaceholder: String,
    val emptyTitle: String,
    val emptyBody: String,
    val tagPrefix: String
) {
    val deleteTitle: String
        get() = "$name 삭제"
}

@Composable
private fun RuleEditorDialog(
    labels: RuleListLabels,
    initialDraft: ContentDraft,
    onSave: suspend (ContentDraft) -> Result<Unit>,
    onDismiss: () -> Unit
) {
    var title by rememberSaveable(initialDraft.id) { mutableStateOf(initialDraft.title) }
    var memo by rememberSaveable(initialDraft.id) { mutableStateOf(initialDraft.body) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (initialDraft.id == 0L) "${labels.name} 추가" else "${labels.name} 수정") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(labels.titleLabel) },
                    placeholder = { Text(labels.titlePlaceholder) },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().testTag("${labels.tagPrefix}_title")
                )
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text(labels.memoLabel) },
                    placeholder = { Text(labels.memoPlaceholder) },
                    minLines = 3,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().testTag("${labels.tagPrefix}_memo")
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && !saving,
                onClick = {
                    saving = true
                    error = null
                    scope.launch {
                        val result = onSave(
                            initialDraft.copy(type = labels.type, title = title.trim(), body = memo.trim())
                        )
                        saving = false
                        if (result.isSuccess) onDismiss()
                        else error = result.exceptionOrNull()?.message ?: "저장하지 못했습니다. 다시 시도해 주세요."
                    }
                }
            ) { Text(if (saving) "저장 중…" else "저장") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("취소") }
        }
    )
}
