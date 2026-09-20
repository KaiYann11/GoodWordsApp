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

internal const val notToDoAddTag = "not_to_do_add"
internal const val notToDoTitleTag = "not_to_do_title"
internal const val notToDoMemoTag = "not_to_do_memo"

/** 계속 확인할 기준을 적습니다. 읽음이나 루틴 수행으로 세지 않습니다. */
@Composable
fun NotToDoScreen(
    items: List<ContentItemEntity>,
    onSave: suspend (ContentDraft) -> Result<Unit>,
    onDelete: suspend (Long) -> Result<Unit>,
    modifier: Modifier = Modifier,
    focusId: Long? = null
) {
    val records = items.filter { it.type == ContentType.NOT_TO_DO }.sortedByDescending { it.createdAt }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember(deletingId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(focusId, records) {
        val index = records.indexOfFirst { it.id == focusId }
        if (index >= 0) listState.animateScrollToItem(index + 1)
    }

    if (editingId != null) {
        NotToDoEditorDialog(
            initialDraft = records.firstOrNull { it.id == editingId }?.let(ContentDraft::fromItem)
                ?: ContentDraft(type = ContentType.NOT_TO_DO),
            onSave = onSave,
            onDismiss = { editingId = null }
        )
    }
    records.firstOrNull { it.id == deletingId }?.let { item ->
        AlertDialog(
            onDismissRequest = { if (!deleting) deletingId = null },
            title = { Text("하지 않을 일 삭제") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("'${item.title}' 항목을 삭제할까요?")
                    deleteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    modifier = Modifier.testTag("not_to_do_confirm_delete"),
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
        modifier = modifier.fillMaxSize().testTag("not_to_do_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("하지 않을 일 ${records.size}개", style = MaterialTheme.typography.titleLarge)
                Text(
                    "반복하지 않기로 한 행동과 그 이유를 적어 두세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { editingId = 0L },
                    modifier = Modifier.fillMaxWidth().testTag(notToDoAddTag)
                ) { Text("하지 않을 일 추가") }
            }
        }
        if (records.isEmpty()) {
            item {
                EmptyCard(
                    title = "아직 기록한 항목이 없습니다.",
                    body = "예: 잠들기 전 숏폼 오래 보기, 화가 난 채로 답장하기. 피하고 싶은 행동부터 하나 적어 보세요."
                )
            }
        }
        items(records, key = { it.id }) { item ->
            Card(
                modifier = Modifier.fillMaxWidth().testTag("not_to_do_item_${item.id}"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, if (item.id == focusId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
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
                            modifier = Modifier.testTag("not_to_do_edit_${item.id}")
                        ) { Text("수정") }
                        TextButton(
                            onClick = { deletingId = item.id },
                            modifier = Modifier.testTag("not_to_do_delete_${item.id}")
                        ) { Text("삭제") }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotToDoEditorDialog(
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
        title = { Text(if (initialDraft.id == 0L) "하지 않을 일 추가" else "하지 않을 일 수정") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("하지 않을 일") },
                    placeholder = { Text("예: 잠들기 전 숏폼 오래 보기") },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().testTag(notToDoTitleTag)
                )
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("메모 (선택)") },
                    placeholder = { Text("피하려는 이유나 기억할 점") },
                    minLines = 3,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().testTag(notToDoMemoTag)
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
                        val result = onSave(initialDraft.copy(type = ContentType.NOT_TO_DO, title = title.trim(), body = memo.trim()))
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
