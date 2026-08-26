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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.TodoBucket
import com.codex.appgoodwords.data.TodoBuckets
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

/** 새 할 일을 언제로 둘지 고르는 칩. */
internal fun todoWhenTag(name: String): String = "todo_when_$name"

/**
 * 새 할 일을 담을 날.
 *
 * 대부분은 오늘 아니면 내일이라 두 번 누르지 않게 칩으로 둡니다. 그 밖의 날은 만들고 나서
 * 고치기에서 고릅니다. 여기에 달력을 붙이면 한 줄로 적어 넣는 빠름이 사라집니다.
 */
private enum class NewTodoWhen(val label: String) {
    TODAY("오늘"),
    TOMORROW("내일"),
    SOMEDAY("언젠가");

    fun dateFrom(today: LocalDate): LocalDate? = when (this) {
        TODAY -> today
        TOMORROW -> today.plusDays(1)
        SOMEDAY -> null
    }
}

/**
 * 할 일.
 *
 * 예전에는 오늘 탭 안에서 루틴과 자리를 나눠 썼고, 화면에는 지난 일과 오늘만 보였습니다.
 * 앞일을 적어 두어도 그날이 오기 전에는 보이지 않아 적어 둘 곳이 못 되었습니다.
 * 지금은 마감일이 없어도 되고([TodoBucket.SOMEDAY]), 앞으로의 일도 칸을 나눠 함께 보입니다.
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
    modifier: Modifier = Modifier,
    /** 검색에서 고른 할 일. 그 자리로 굴려 주고 잠깐 강조합니다. */
    focusId: Long? = null
) {
    var editing by remember { mutableStateOf<TodoDraft?>(null) }
    var pendingDelete by remember { mutableStateOf<TodoEntity?>(null) }
    var newTitle by remember { mutableStateOf("") }
    var newWhen by rememberSaveable { mutableStateOf(NewTodoWhen.TODAY.name) }

    val selectedWhen = NewTodoWhen.valueOf(newWhen)
    val buckets = remember(todos, today) { TodoBuckets.group(todos, today) }
    val remainingToday = TodoBuckets.remainingCount(buckets[TodoBucket.TODAY].orEmpty())
    val remainingOverdue = TodoBuckets.remainingCount(buckets[TodoBucket.OVERDUE].orEmpty())

    val listState = rememberLazyListState()
    // 안내 카드 하나를 지나고, 칸마다 제목 한 줄과 그 칸의 할 일들이 이어집니다.
    val focusIndex = remember(focusId, buckets) {
        if (focusId == null) return@remember null
        var index = 1
        for ((_, list) in buckets) {
            index += 1
            val found = list.indexOfFirst { it.id == focusId }
            if (found >= 0) return@remember index + found
            index += list.size
        }
        null
    }
    ScrollToFocus(listState = listState, index = focusIndex, key = focusId)

    LazyColumn(
        state = listState,
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
                        text = when {
                            remainingOverdue > 0 -> "오늘 남은 일 ${remainingToday}개 · 지난 일 ${remainingOverdue}개"
                            remainingToday > 0 -> "오늘 남은 일 ${remainingToday}개"
                            else -> "오늘 할 일을 다 끝냈습니다."
                        },
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
                                onSaveTodo(
                                    TodoDraft(
                                        title = newTitle,
                                        dueDate = selectedWhen.dateFrom(today)
                                    )
                                )
                                newTitle = ""
                            },
                            modifier = Modifier.testTag(todoAddButtonTag)
                        ) {
                            Text("추가")
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(NewTodoWhen.entries) { choice ->
                            FilterChip(
                                selected = selectedWhen == choice,
                                onClick = { newWhen = choice.name },
                                label = { Text(choice.label) },
                                modifier = Modifier.testTag(todoWhenTag(choice.name))
                            )
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

        if (todos.isEmpty()) {
            item {
                EmptyCard(
                    title = "적어 둔 할 일이 없습니다.",
                    body = "오늘 것도, 다음 주 것도, 날짜를 정하지 않은 것도 여기에 함께 모입니다."
                )
            }
        }

        buckets.forEach { (bucket, list) ->
            item(key = "head-${bucket.name}") {
                val remaining = TodoBuckets.remainingCount(list)
                Text(
                    text = if (remaining > 0) "${bucket.label} ${remaining}개" else bucket.label,
                    style = MaterialTheme.typography.titleSmall,
                    // 지난 일만 붉게 둡니다. 다 물들이면 어느 것이 급한지 알 수 없습니다.
                    color = if (bucket == TodoBucket.OVERDUE) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.testTag(todoBucketTag(bucket.name))
                )
            }

            items(list, key = { "${bucket.name}-${it.id}" }) { todo ->
                TodoRow(
                    todo = todo,
                    focused = todo.id == focusId,
                    focusKey = focusId,
                    isOverdue = bucket == TodoBucket.OVERDUE && !todo.isDone,
                    onToggleDone = { onToggleDone(todo.id) },
                    onEdit = { editing = TodoDraft.from(todo) },
                    onDelete = { pendingDelete = todo }
                )
            }
        }
    }

    editing?.let { draft ->
        TodoEditDialog(
            draft = draft,
            today = today,
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

/** 칸 제목. 화면에는 같은 글자가 여럿이라 표식으로 짚습니다. */
internal fun todoBucketTag(name: String): String = "todo_bucket_$name"

@Composable
private fun TodoRow(
    todo: TodoEntity,
    isOverdue: Boolean,
    onToggleDone: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    focused: Boolean = false,
    focusKey: Any? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .focusHighlight(focused = focused, key = focusKey)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = todo.isDone, onCheckedChange = { onToggleDone() })
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = todo.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (todo.isDone) TextDecoration.LineThrough else null,
                    color = if (isOverdue) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                val subtitle = listOfNotNull(
                    todo.dueDate.takeIf { it.isNotBlank() },
                    todo.remindAt?.let { "알람 ${formatTime(it)}" },
                    todo.note.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(onClick = onEdit) { Text("고치기") }
            IconButton(onClick = onDelete) {
                Icon(imageVector = Icons.Outlined.Delete, contentDescription = "할 일 지우기")
            }
        }
    }
}

@Composable
private fun TodoEditDialog(
    draft: TodoDraft,
    today: LocalDate,
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
                        val date = current.dueDate ?: today
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
                    Text(current.dueDate?.let { "날짜 $it" } ?: "날짜 없음 (언젠가)")
                }
                if (current.dueDate != null) {
                    // 날짜를 지우면 알람도 함께 지웁니다. 붙을 날이 없는 알람은 언제 울릴지 정할 수 없습니다.
                    TextButton(
                        onClick = { current = current.copy(dueDate = null, remindAt = null) },
                        modifier = Modifier.testTag(todoClearDateTag)
                    ) {
                        Text("날짜 지우기")
                    }
                }
                OutlinedButton(
                    // 날짜가 없으면 알람을 걸 날이 없습니다.
                    enabled = current.dueDate != null,
                    onClick = {
                        val dueDate = current.dueDate ?: return@OutlinedButton
                        val base = current.remindAt?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime()
                        } ?: LocalDateTime.of(dueDate, java.time.LocalTime.of(9, 0))
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                current = current.copy(remindAt = toEpochMillis(dueDate, hour, minute))
                            },
                            base.hour,
                            base.minute,
                            true
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        current.remindAt?.let { "알람 ${formatTime(it)}" }
                            ?: if (current.dueDate == null) "날짜를 정해야 알람을 걸 수 있습니다" else "알람 없음"
                    )
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

internal const val todoClearDateTag = "todo_clear_date"

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
