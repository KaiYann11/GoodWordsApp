package com.codex.appgoodwords.ui.screen

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.TodoDraft
import com.codex.appgoodwords.data.TodoEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

internal const val todoInputTag = "todo_input"
internal const val todoAddButtonTag = "todo_add_button"

/**
 * 오늘 해야 할 일.
 *
 * 못 끝낸 지난 일은 사라지지 않고 위쪽에 따로 모입니다.
 * 오늘 새로 정한 일과 섞으면 밀린 일이 며칠 쌓였을 때 목록을 읽을 수 없게 됩니다.
 */
@Composable
fun TodoScreen(
    todos: List<TodoEntity>,
    today: LocalDate,
    canScheduleExactAlarms: Boolean,
    onSaveTodo: (TodoDraft) -> Unit,
    onToggleDone: (Long) -> Unit,
    onDeleteTodo: (Long) -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf<TodoDraft?>(null) }
    var pendingDelete by remember { mutableStateOf<TodoEntity?>(null) }
    var newTitle by remember { mutableStateOf("") }

    val overdue = todos.filter { it.isOverdueOn(today) }.sortedBy { it.dueDate }
    val todayList = todos.filter { it.dueDate == today.toString() }
    val remaining = todayList.count { !it.isDone }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (remaining > 0) "오늘 남은 일 ${remaining}개" else "오늘 할 일을 다 끝냈습니다.",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newTitle,
                            onValueChange = { newTitle = it },
                            label = { Text("할 일") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag(todoInputTag)
                        )
                        Button(
                            enabled = newTitle.isNotBlank(),
                            onClick = {
                                onSaveTodo(TodoDraft(title = newTitle, dueDate = today))
                                newTitle = ""
                            },
                            modifier = Modifier.testTag(todoAddButtonTag)
                        ) {
                            Text("추가")
                        }
                    }
                    if (!canScheduleExactAlarms) {
                        Text(
                            text = "정확한 알람 권한이 없어 알람이 늦게 울릴 수 있습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = onOpenExactAlarmSettings) {
                            Text("알람 권한 설정 열기")
                        }
                    }
                }
            }
        }

        if (overdue.isNotEmpty()) {
            item {
                Text(
                    text = "지난 일 ${overdue.size}개",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            items(overdue, key = { "overdue-${it.id}" }) { todo ->
                TodoRow(
                    todo = todo,
                    isOverdue = true,
                    onToggleDone = { onToggleDone(todo.id) },
                    onEdit = { editing = TodoDraft.from(todo) },
                    onDelete = { pendingDelete = todo }
                )
            }
        }

        item {
            Text("오늘", style = MaterialTheme.typography.titleSmall)
        }

        if (todayList.isEmpty()) {
            item {
                Text(
                    text = "오늘 정한 할 일이 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(todayList, key = { "today-${it.id}" }) { todo ->
            TodoRow(
                todo = todo,
                isOverdue = false,
                onToggleDone = { onToggleDone(todo.id) },
                onEdit = { editing = TodoDraft.from(todo) },
                onDelete = { pendingDelete = todo }
            )
        }
    }

    editing?.let { draft ->
        TodoEditDialog(
            draft = draft,
            onDismiss = { editing = null },
            onSave = {
                onSaveTodo(it)
                editing = null
            }
        )
    }

    pendingDelete?.let { todo ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("할 일을 지울까요?") },
            text = { Text("\"${todo.title}\"을(를) 지웁니다. 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteTodo(todo.id)
                    pendingDelete = null
                }) {
                    Text("지우기")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("취소") }
            }
        )
    }
}

@Composable
private fun TodoRow(
    todo: TodoEntity,
    isOverdue: Boolean,
    onToggleDone: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = todo.isDone, onCheckedChange = { onToggleDone() })
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = todo.title,
                    style = MaterialTheme.typography.bodyLarge,
                    // 끝낸 일은 목록에 남되 한눈에 구분되어야 합니다.
                    textDecoration = if (todo.isDone) TextDecoration.LineThrough else null
                )
                val subtitle = buildList {
                    if (isOverdue) add(todo.dueDate)
                    todo.remindAt?.let { add("알람 ${formatTime(it)}") }
                    if (todo.note.isNotBlank()) add(todo.note)
                }.joinToString(" · ")
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOverdue) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            TextButton(onClick = onEdit) { Text("수정") }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "지우기")
            }
        }
    }
}

@Composable
private fun TodoEditDialog(
    draft: TodoDraft,
    onDismiss: () -> Unit,
    onSave: (TodoDraft) -> Unit
) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(draft) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("할 일 고치기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = current.title,
                    onValueChange = { current = current.copy(title = it) },
                    label = { Text("할 일") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = current.note,
                    onValueChange = { current = current.copy(note = it) },
                    label = { Text("메모") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        val date = current.dueDate
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                current = current.copy(dueDate = LocalDate.of(year, month + 1, dayOfMonth))
                            },
                            date.year,
                            date.monthValue - 1,
                            date.dayOfMonth
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("날짜 ${current.dueDate}")
                }
                OutlinedButton(
                    onClick = {
                        val base = current.remindAt?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime()
                        } ?: LocalDateTime.of(current.dueDate, java.time.LocalTime.of(9, 0))
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                current = current.copy(remindAt = toEpochMillis(current.dueDate, hour, minute))
                            },
                            base.hour,
                            base.minute,
                            true
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(current.remindAt?.let { "알람 ${formatTime(it)}" } ?: "알람 없음")
                }
                if (current.remindAt != null) {
                    TextButton(onClick = { current = current.copy(remindAt = null) }) {
                        Text("알람 지우기")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = current.title.isNotBlank(),
                onClick = { onSave(current) }
            ) {
                Text("저장")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

/** 날짜를 바꾸면 알람도 그 날짜로 따라가야 합니다. 시각만 남으면 지난 날에 걸립니다. */
private fun toEpochMillis(date: LocalDate, hour: Int, minute: Int): Long {
    val calendar = Calendar.getInstance().apply {
        set(date.year, date.monthValue - 1, date.dayOfMonth, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return calendar.timeInMillis
}

private fun formatTime(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("M월 d일 HH:mm"))
