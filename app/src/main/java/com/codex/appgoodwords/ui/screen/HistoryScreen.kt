package com.codex.appgoodwords.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.ExposureEventEntity
import com.codex.appgoodwords.data.ExposureEventType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(
    events: List<ExposureEventEntity>,
    onOpenItem: (Long) -> Unit,
    onDeleteEvents: (Set<Long>, () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var selectedEventIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    val sections = remember(events) {
        events
            .filter { it.eventType == ExposureEventType.CONFIRMED }
            .groupBy { event ->
                Instant.ofEpochMilli(event.occurredAt)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
            }
            .entries
            .sortedByDescending { it.key }
            .map { (date, dayEvents) ->
                HistorySection(
                    date = date,
                    confirmedEvents = dayEvents
                )
            }
    }

    LaunchedEffect(events) {
        val availableIds = events.map { it.id }.toSet()
        selectedEventIds = selectedEventIds.intersect(availableIds)
        if (selectionMode && selectedEventIds.isEmpty()) {
            selectionMode = false
        }
    }

    fun clearSelection() {
        selectionMode = false
        selectedEventIds = emptySet()
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("선택한 이력을 삭제할까요?") },
            text = { Text("${selectedEventIds.size}개 이력을 삭제합니다. 삭제 후에는 복구할 수 없습니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteEvents(selectedEventIds) {
                            clearSelection()
                        }
                    }
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("취소")
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
                    Text(
                        text = "날짜별로 확인한 항목을 볼 수 있습니다. 날짜 헤더를 길게 누르면 그 날짜 이력을 한 번에 지울 수 있습니다.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (selectionMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showDeleteDialog = true },
                                enabled = selectedEventIds.isNotEmpty(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = null
                                )
                                Text("선택 삭제 ${selectedEventIds.size}")
                            }
                            OutlinedButton(
                                onClick = { clearSelection() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("선택 취소")
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { selectionMode = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("이력 선택 삭제")
                        }
                    }
                }
            }
        }

        if (sections.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "아직 이력이 없습니다.",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "오늘의 글귀를 보거나 알림을 받으면 이력에 기록됩니다.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        } else {
            items(sections, key = { it.date.toString() }) { section ->
                HistorySectionCard(
                    section = section,
                    selectionMode = selectionMode,
                    selectedEventIds = selectedEventIds,
                    onToggleSelection = { eventId ->
                        selectedEventIds = if (eventId in selectedEventIds) {
                            selectedEventIds - eventId
                        } else {
                            selectedEventIds + eventId
                        }
                    },
                    onDeleteGroup = { ids, onDeleted ->
                        onDeleteEvents(ids, onDeleted)
                    },
                    onOpenItem = onOpenItem
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistorySectionCard(
    section: HistorySection,
    selectionMode: Boolean,
    selectedEventIds: Set<Long>,
    onToggleSelection: (Long) -> Unit,
    onDeleteGroup: (Set<Long>, () -> Unit) -> Unit,
    onOpenItem: (Long) -> Unit
) {
    var expanded by rememberSaveable(section.date.toString()) { mutableStateOf(true) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val sectionEventIds = remember(section) {
        section.confirmedEvents.map { it.id }.toSet()
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("${section.date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))} 이력을 삭제할까요?") },
            text = { Text("${sectionEventIds.size}개 이력을 한 번에 삭제합니다. 삭제 후에는 복구할 수 없습니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteGroup(sectionEventIds) { }
                    }
                ) {
                    Text("전체 삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { expanded = !expanded },
                        onLongClick = {
                            if (sectionEventIds.isNotEmpty()) {
                                menuExpanded = true
                            }
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = section.date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "확인 ${section.confirmedEvents.size}건",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "날짜 그룹 접기" else "날짜 그룹 펼치기"
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("이 날짜 전체 삭제") },
                    onClick = {
                        menuExpanded = false
                        showDeleteDialog = true
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null
                        )
                    }
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HistoryEventGroup(
                        title = "확인한 항목",
                        events = section.confirmedEvents,
                        selectionMode = selectionMode,
                        selectedEventIds = selectedEventIds,
                        emptyMessage = "이 날짜에는 확인 기록이 없습니다.",
                        onToggleSelection = onToggleSelection,
                        onOpenItem = onOpenItem
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryEventGroup(
    title: String,
    events: List<ExposureEventEntity>,
    selectionMode: Boolean,
    selectedEventIds: Set<Long>,
    emptyMessage: String,
    onToggleSelection: (Long) -> Unit,
    onOpenItem: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall
        )

        if (events.isEmpty()) {
            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            events.forEach { event ->
                HistoryEventRow(
                    event = event,
                    selectionMode = selectionMode,
                    selected = event.id in selectedEventIds,
                    onToggleSelection = onToggleSelection,
                    onOpenItem = onOpenItem
                )
            }
        }
    }
}

@Composable
private fun HistoryEventRow(
    event: ExposureEventEntity,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelection: (Long) -> Unit,
    onOpenItem: (Long) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                } else {
                    Color.Transparent
                }
            )
            .clickable {
                if (selectionMode) {
                    onToggleSelection(event.id)
                } else {
                    onOpenItem(event.contentItemId)
                }
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (selectionMode) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                }
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "${formatTime(event.occurredAt)}  ${event.contentTitle}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "확인 기록",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class HistorySection(
    val date: LocalDate,
    val confirmedEvents: List<ExposureEventEntity>
)

private fun formatTime(timestamp: Long): String {
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
}
