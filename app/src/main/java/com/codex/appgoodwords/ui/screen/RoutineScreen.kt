package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.RoutineCheckEntity
import com.codex.appgoodwords.data.RoutineDraft
import com.codex.appgoodwords.data.RoutineEntity
import com.codex.appgoodwords.data.RoutineMemoEntity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RoutineScreen(
    routines: List<RoutineEntity>,
    todayCounts: Map<Long, Int>,
    checks: List<RoutineCheckEntity>,
    memos: List<RoutineMemoEntity>,
    onSaveRoutine: (RoutineDraft) -> Unit,
    onDeleteRoutine: (RoutineEntity) -> Unit,
    onSaveMemo: (RoutineEntity, String) -> Unit,
    onDeleteMemo: (RoutineMemoEntity) -> Unit,
    onCheckRoutine: (RoutineEntity) -> Unit,
    modifier: Modifier = Modifier,
    /** 검색에서 고른 루틴. 그 자리로 굴려 주고 잠깐 강조합니다. */
    focusId: Long? = null
) {
    var editingRoutineId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showDeleteRoutineId by rememberSaveable { mutableStateOf<Long?>(null) }
    var memoRoutineId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedMonthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedDateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val editingRoutine = routines.firstOrNull { it.id == editingRoutineId }
    val deletingRoutine = routines.firstOrNull { it.id == showDeleteRoutineId }
    val memoRoutine = routines.firstOrNull { it.id == memoRoutineId }
    val memosByRoutine = memos.groupBy { it.routineId }
    val sortedRoutines = routines.sortedWith(
        compareBy<RoutineEntity> { (todayCounts[it.id] ?: 0) > 0 }
            .thenByDescending { it.createdAt }
    )

    if (editingRoutineId != null) {
        RoutineEditorDialog(
            initialDraft = editingRoutine?.let(RoutineDraft::fromRoutine) ?: RoutineDraft(),
            onDismiss = { editingRoutineId = null },
            onSave = { draft ->
                onSaveRoutine(draft)
                editingRoutineId = null
            }
        )
    }

    if (deletingRoutine != null) {
        AlertDialog(
            onDismissRequest = { showDeleteRoutineId = null },
            title = { Text("루틴 삭제") },
            text = { Text("'${deletingRoutine.title}' 루틴을 삭제할까요? 오늘까지의 수행 이력도 함께 삭제됩니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteRoutine(deletingRoutine)
                        showDeleteRoutineId = null
                    }
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteRoutineId = null }) {
                    Text("취소")
                }
            }
        )
    }

    if (memoRoutine != null) {
        RoutineMemoDialog(
            routine = memoRoutine,
            memos = memosByRoutine[memoRoutine.id].orEmpty(),
            onDismiss = { memoRoutineId = null },
            onSaveMemo = { body -> onSaveMemo(memoRoutine, body) },
            onDeleteMemo = onDeleteMemo
        )
    }

    val listState = rememberLazyListState()
    // 추가 버튼 1 + 달력 카드 1을 지나야 루틴 목록이 시작합니다.
    val focusIndex = remember(focusId, sortedRoutines) {
        sortedRoutines.indexOfFirst { it.id == focusId }.takeIf { it >= 0 }?.plus(2)
    }
    ScrollToFocus(listState = listState, index = focusIndex, key = focusId)

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Button(
                onClick = { editingRoutineId = 0L },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null
                )
                Text("루틴 추가")
            }
        }

        item {
            RoutineCalendarCard(
                routines = routines,
                checks = checks,
                selectedMonthText = selectedMonthText,
                selectedDateText = selectedDateText,
                onMonthChanged = { month, date ->
                    selectedMonthText = month.toString()
                    selectedDateText = date.toString()
                },
                onDateSelected = { date ->
                    selectedDateText = date.toString()
                }
            )
        }

        if (routines.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "등록된 루틴이 없습니다.",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "반복해서 수행할 일을 추가하면 오늘 수행 횟수를 누적할 수 있습니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(sortedRoutines, key = { it.id }) { routine ->
                RoutineCard(
                    routine = routine,
                    focused = routine.id == focusId,
                    focusKey = focusId,
                    todayCount = todayCounts[routine.id] ?: 0,
                    memoCount = memosByRoutine[routine.id]?.size ?: 0,
                    latestMemo = memosByRoutine[routine.id]?.maxByOrNull { it.createdAt },
                    onOpenMemos = { memoRoutineId = routine.id },
                    onCheckRoutine = onCheckRoutine,
                    onEditRoutine = { editingRoutineId = routine.id },
                    onDeleteRoutine = { showDeleteRoutineId = routine.id }
                )
            }
        }
    }
}

@Composable
private fun RoutineCalendarCard(
    routines: List<RoutineEntity>,
    checks: List<RoutineCheckEntity>,
    selectedMonthText: String,
    selectedDateText: String,
    onMonthChanged: (YearMonth, LocalDate) -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    val today = LocalDate.now()
    val currentMonth = YearMonth.from(today)
    val selectedMonth = runCatching { YearMonth.parse(selectedMonthText) }.getOrDefault(currentMonth)
    val selectedDate = runCatching { LocalDate.parse(selectedDateText) }.getOrDefault(
        if (selectedMonth == currentMonth) today else selectedMonth.atDay(1)
    )
    val monthStats = buildRoutineMonthStats(
        routines = routines,
        checks = checks,
        month = selectedMonth
    )
    val selectedChecks = checksForDate(checks, selectedDate)
    val rateText = if (monthStats.totalSlots > 0) {
        "${monthStats.ratePercent}%"
    } else {
        "-"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val target = selectedMonth.minusMonths(1)
                        onMonthChanged(target, target.atDay(1))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("이전")
                }
                Column(
                    modifier = Modifier.weight(1.8f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${selectedMonth.year}년 ${selectedMonth.monthValue}월",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "달성률 $rateText (${monthStats.completedSlots}/${monthStats.totalSlots})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                OutlinedButton(
                    onClick = {
                        val target = selectedMonth.plusMonths(1)
                        val targetDate = if (target == currentMonth) today else target.atDay(1)
                        onMonthChanged(target, targetDate)
                    },
                    enabled = selectedMonth.isBefore(currentMonth),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("다음")
                }
            }

            RoutineCalendarGrid(
                days = monthStats.days,
                selectedDate = selectedDate,
                onDateSelected = onDateSelected
            )

            RoutineSelectedDayHistory(
                date = selectedDate,
                checks = selectedChecks
            )
        }
    }
}

@Composable
private fun RoutineCalendarGrid(
    days: List<RoutineCalendarDay>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    val firstOffset = days.firstOrNull()?.date?.dayOfWeek?.value?.rem(7) ?: 0
    val cells = List(firstOffset) { null } + days
    val paddedCells = cells + List((7 - cells.size % 7) % 7) { null }
    val weekLabels = listOf("일", "월", "화", "수", "목", "금", "토")

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            weekLabels.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        paddedCells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { day ->
                    if (day == null) {
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 72.dp)
                        )
                    } else {
                        RoutineCalendarDayCell(
                            day = day,
                            selected = day.date == selectedDate,
                            onClick = { onDateSelected(day.date) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutineCalendarDayCell(
    day: RoutineCalendarDay,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val completedAll = day.total > 0 && day.completed >= day.total
    val completedSome = day.completed > 0
    val containerColor = when {
        completedAll -> MaterialTheme.colorScheme.primaryContainer
        completedSome -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.surface
    }
    val border = if (selected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }

    Card(
        modifier = modifier
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = border
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (day.total > 0) "${day.completed}/${day.total}" else "-",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            if (day.checkCount > day.completed) {
                Text(
                    text = "${day.checkCount}회",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun RoutineSelectedDayHistory(
    date: LocalDate,
    checks: List<RoutineCheckEntity>
) {
    val grouped = checks
        .groupBy { it.routineId }
        .values
        .sortedBy { group -> group.firstOrNull()?.routineTitle.orEmpty() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "${date.format(DateTimeFormatter.ofPattern("M월 d일"))} 기록",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        if (grouped.isEmpty()) {
            Text(
                text = "수행 기록이 없습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            grouped.forEach { group ->
                val title = group.firstOrNull()?.routineTitle.orEmpty().ifBlank { "삭제된 루틴" }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${group.size}회",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun RoutineCard(
    routine: RoutineEntity,
    todayCount: Int,
    memoCount: Int,
    latestMemo: RoutineMemoEntity?,
    onOpenMemos: () -> Unit,
    onCheckRoutine: (RoutineEntity) -> Unit,
    onEditRoutine: () -> Unit,
    onDeleteRoutine: () -> Unit,
    focused: Boolean = false,
    focusKey: Any? = null
) {
    val checkedToday = todayCount > 0
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .focusHighlight(focused = focused, key = focusKey),
        colors = CardDefaults.cardColors(
            containerColor = if (checkedToday) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.62f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (checkedToday) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f))
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = routine.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (checkedToday) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    if (routine.note.isNotBlank()) {
                        Text(
                            text = routine.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (routine.category.isNotBlank()) {
                        Text(
                            text = routine.category,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    if (latestMemo != null) {
                        Text(
                            text = "최근 메모: ${latestMemo.body}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onEditRoutine) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "루틴 수정"
                    )
                }
                IconButton(onClick = onDeleteRoutine) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "루틴 삭제"
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "오늘 $todayCount 회",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (checkedToday) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        text = when {
                            checkedToday -> "오늘 수행됨 · ${if (routine.reminderEnabled) "알림 포함" else "알림 제외"}"
                            routine.reminderEnabled -> "알림 포함"
                            else -> "알림 제외"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onOpenMemos,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("메모 $memoCount")
                }
                Button(
                    onClick = { onCheckRoutine(routine) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null
                    )
                    Text("수행 +1")
                }
            }
        }
    }
}

@Composable
private fun RoutineMemoDialog(
    routine: RoutineEntity,
    memos: List<RoutineMemoEntity>,
    onDismiss: () -> Unit,
    onSaveMemo: (String) -> Unit,
    onDeleteMemo: (RoutineMemoEntity) -> Unit
) {
    var memoText by rememberSaveable(routine.id) { mutableStateOf("") }
    val sortedMemos = memos.sortedByDescending { it.createdAt }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${routine.title} 메모") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = memoText,
                    onValueChange = { memoText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("메모 기록") },
                    minLines = 3
                )
                Button(
                    onClick = {
                        onSaveMemo(memoText)
                        memoText = ""
                    },
                    enabled = memoText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("메모 저장")
                }

                if (sortedMemos.isEmpty()) {
                    Text(
                        text = "아직 메모가 없습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sortedMemos, key = { it.id }) { memo ->
                            RoutineMemoRow(
                                memo = memo,
                                onDeleteMemo = onDeleteMemo
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}

@Composable
private fun RoutineMemoRow(
    memo: RoutineMemoEntity,
    onDeleteMemo: (RoutineMemoEntity) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = memo.body,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = formatMemoTime(memo.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { onDeleteMemo(memo) }) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "메모 삭제"
                )
            }
        }
    }
}

@Composable
private fun RoutineEditorDialog(
    initialDraft: RoutineDraft,
    onDismiss: () -> Unit,
    onSave: (RoutineDraft) -> Unit
) {
    var title by rememberSaveable(initialDraft.id) { mutableStateOf(initialDraft.title) }
    var note by rememberSaveable(initialDraft.id) { mutableStateOf(initialDraft.note) }
    var category by rememberSaveable(initialDraft.id) { mutableStateOf(initialDraft.category) }
    var reminderEnabled by rememberSaveable(initialDraft.id) {
        mutableStateOf(initialDraft.reminderEnabled)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialDraft.id == 0L) "루틴 추가" else "루틴 수정")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("루틴 이름") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("메모") },
                    minLines = 2
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("카테고리") },
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("주기 알림에 포함")
                    Switch(
                        checked = reminderEnabled,
                        onCheckedChange = { reminderEnabled = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        initialDraft.copy(
                            title = title,
                            note = note,
                            category = category,
                            reminderEnabled = reminderEnabled
                        )
                    )
                },
                enabled = title.isNotBlank()
            ) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

private fun formatMemoTime(timestamp: Long): String {
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}

private data class RoutineCalendarDay(
    val date: LocalDate,
    val completed: Int,
    val total: Int,
    val checkCount: Int
)

private data class RoutineMonthStats(
    val days: List<RoutineCalendarDay>,
    val completedSlots: Int,
    val totalSlots: Int
) {
    val ratePercent: Int
        get() = if (totalSlots == 0) 0 else ((completedSlots * 100.0) / totalSlots).toInt()
}

private fun buildRoutineMonthStats(
    routines: List<RoutineEntity>,
    checks: List<RoutineCheckEntity>,
    month: YearMonth
): RoutineMonthStats {
    val today = LocalDate.now()
    val days = (1..month.lengthOfMonth()).map { day ->
        val date = month.atDay(day)
        if (date.isAfter(today)) {
            RoutineCalendarDay(
                date = date,
                completed = 0,
                total = 0,
                checkCount = 0
            )
        } else {
            val checksForDay = checksForDate(checks, date)
            val checkedRoutineIds = checksForDay.map { it.routineId }.toSet()
            val activeRoutineIds = routines
                .filter { routine -> routine.createdAt <= endOfDayMillis(date) }
                .map { it.id }
                .toSet()
            val totalRoutineIds = activeRoutineIds + checkedRoutineIds

            RoutineCalendarDay(
                date = date,
                completed = checkedRoutineIds.size,
                total = totalRoutineIds.size,
                checkCount = checksForDay.size
            )
        }
    }

    return RoutineMonthStats(
        days = days,
        completedSlots = days.sumOf { it.completed },
        totalSlots = days.sumOf { it.total }
    )
}

private fun checksForDate(
    checks: List<RoutineCheckEntity>,
    date: LocalDate
): List<RoutineCheckEntity> {
    val start = startOfDayMillis(date)
    val end = endOfDayMillis(date)
    return checks.filter { it.checkedAt in start..end }
}

private fun startOfDayMillis(date: LocalDate): Long {
    return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

private fun endOfDayMillis(date: LocalDate): Long {
    return date.plusDays(1)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli() - 1
}
