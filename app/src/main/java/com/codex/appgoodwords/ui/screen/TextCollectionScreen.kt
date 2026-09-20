package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ContentMemoEntity
import com.codex.appgoodwords.data.ContentType
import com.codex.appgoodwords.data.ConceptExplanation

internal const val inboxPasteTag = "inbox_paste"
internal fun inboxClassifyTag(id: Long) = "inbox_classify_$id"
internal fun inboxAskConceptTag(id: Long) = "inbox_ask_concept_$id"

/** 수집한 글과 정리한 메모는 읽음 횟수를 세지 않고 최신순으로 찾습니다. */
@Composable
fun TextCollectionScreen(
    items: List<ContentItemEntity>,
    collectionType: ContentType,
    onOpenItem: (ContentItemEntity) -> Unit,
    modifier: Modifier = Modifier,
    contentMemos: List<ContentMemoEntity> = emptyList(),
    onPaste: (String) -> Unit = {},
    onClassifyItem: (ContentItemEntity) -> Unit = {},
    askingConceptId: Long? = null,
    onAskConcept: (ContentItemEntity) -> Unit = {}
) {
    val isInbox = collectionType == ContentType.INBOX
    val isConcept = collectionType == ContentType.CONCEPT
    var query by rememberSaveable(collectionType) { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val label = when (collectionType) {
        ContentType.INBOX -> "임시저장소"
        ContentType.CONCEPT -> "개념"
        else -> "메모"
    }
    val memoBodiesByItem = contentMemos.groupBy { it.contentItemId }
    val filtered = items.filter { item ->
        query.isBlank() || (listOf(item.title, item.body, item.sourceUrl, item.tags.joinToString(" ")) +
            memoBodiesByItem[item.id].orEmpty().map { it.body })
            .any { it.contains(query.trim(), ignoreCase = true) }
    }.sortedByDescending { it.createdAt }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "$label ${items.size}",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (isInbox) {
                        Button(
                            onClick = { onPaste(clipboard.getText()?.text.orEmpty()) },
                            modifier = Modifier.testTag(inboxPasteTag)
                        ) {
                            Text("붙여넣기")
                        }
                    }
                }
                Text(
                    text = when {
                        isInbox -> "먼저 담아 두고, AI에게 뜻을 묻거나 직접 정리하세요."
                        isConcept -> "원문과 AI에게 받은 설명을 함께 모아 둔 곳입니다."
                        else -> "참고할 정보와 문장을 모아 둔 곳입니다."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("$label 검색") },
                    singleLine = true,
                    trailingIcon = if (query.isNotEmpty()) {
                        { TextButton(onClick = { query = "" }) { Text("지우기") } }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (filtered.isEmpty()) {
            item {
                EmptyCard(
                    title = if (query.isNotBlank()) "검색 결과가 없습니다."
                        else if (isInbox) "정리할 글이 없습니다."
                        else if (isConcept) "아직 정리한 개념이 없습니다."
                        else "아직 메모가 없습니다.",
                    body = if (query.isNotBlank()) "다른 검색어로 찾아보세요."
                        else if (isInbox) "다른 앱에서 텍스트를 공유하거나, 복사한 뒤 위의 붙여넣기를 눌러 주세요."
                        else if (isConcept) "임시저장소에서 모르는 용어나 문장을 AI에게 물어보세요."
                        else "임시저장소에서 글을 골라 메모로 옮겨 보세요."
                )
            }
        }
        items(filtered, key = { it.id }) { item ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpenItem(item) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (item.body.isNotBlank() && !item.body.startsWith(item.title)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    }
                    Text(
                        text = item.body.ifBlank { item.title },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.sourceUrl.isNotBlank()) {
                        Text(
                            text = item.sourceUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (isConcept) {
                        memoBodiesByItem[item.id]
                            .orEmpty()
                            .sortedBy { it.createdAt }
                            .firstNotNullOfOrNull { ConceptExplanation.fromMemo(it.body) }
                            ?.let { explanation ->
                                Text("AI 설명", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = explanation,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 8,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                    }
                    if (isInbox) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { onAskConcept(item) },
                                enabled = askingConceptId == null,
                                modifier = Modifier.fillMaxWidth().testTag(inboxAskConceptTag(item.id))
                            ) {
                                Text(if (askingConceptId == item.id) "AI에게 묻는 중…" else "AI에게 의미 묻기")
                            }
                            OutlinedButton(
                                onClick = { onClassifyItem(item) },
                                enabled = askingConceptId == null,
                                modifier = Modifier.fillMaxWidth().testTag(inboxClassifyTag(item.id))
                            ) {
                                Text("직접 분류하기")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClassifyInboxDialog(
    item: ContentItemEntity,
    saving: Boolean,
    onSelect: (ContentType) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("어디로 옮길까요?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.body.ifBlank { item.title }, maxLines = 4, overflow = TextOverflow.Ellipsis)
                listOf(
                    ContentType.MEMO to "메모로 이동",
                    ContentType.QUOTE to "글귀로 이동",
                    ContentType.IDEA to "아이디어로 이동"
                ).forEach { (type, label) ->
                    TextButton(
                        onClick = { onSelect(type) },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(label) }
                }
                if (saving) Text("옮기는 중…")
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("취소") }
        }
    )
}
