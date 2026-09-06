package com.codex.appgoodwords.ui.screen

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.RoutineCheckEntity
import com.codex.appgoodwords.data.RoutineDraft
import com.codex.appgoodwords.data.RoutineEntity
import com.codex.appgoodwords.data.RoutineMemoEntity
import com.codex.appgoodwords.data.RoutineOrder
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** 차례 옮기기 대화상자에서 [step]번째 자리를 가리키는 줄. 카드에도 같은 이름이 있어 태그로 짚습니다. */
internal fun routineMoveTargetTag(step: Int): String = "routine_move_target_$step"

/** 오늘 무엇을 볼지 고르는 거르개. 개수가 같이 적혀서 글자만으로는 짚기 어렵습니다. */
internal fun routineFilterTag(name: String): String = "routine_filter_$name"

/** 추가 버튼·진행 카드·거르개. 목록은 이 뒤부터 시작합니다. */
private const val HEADER_ITEM_COUNT = 3

/**
 * 오늘 무엇을 볼지 고르는 거르개.
 *
 * 루틴이 서른 개가 넘으면 다 끝낸 것까지 함께 굴려야 남은 것을 찾을 수 있습니다.
 */
private enum class RoutineFilter(val label: String) {
    ALL("전체"),
    UNDONE("안 한 것"),
    DONE("한 것");

    fun matches(todayCount: Int): Boolean = when (this) {
        ALL -> true
        UNDONE -> todayCount == 0
        DONE -> todayCount > 0
    }
}

/**
 * 화면에 놓을 루틴 한 줄.
 *
 * [step]은 걸러 내기 전 온전한 줄에서의 차례입니다. 걸러 놓고 다시 세면 "안 한 것"만 볼 때
 * 3번이던 루틴이 1번으로 보여, 옮기기가 엉뚱한 자리를 가리킵니다.
 */
private data class RoutineRow(
    val routine: RoutineEntity,
    val step: Int,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean
)

@Composable
fun RoutineScreen(
    routines: List<RoutineEntity>,
    todayCounts: Map<Long, Int>,
    memos: List<RoutineMemoEntity>,
    onSaveRoutine: (RoutineDraft) -> Unit,
    onDeleteRoutine: (RoutineEntity) -> Unit,
    onSaveMemo: (RoutineEntity, String) -> Unit,
    onDeleteMemo: (RoutineMemoEntity) -> Unit,
    onCheckRoutine: (RoutineEntity) -> Unit,
    /** 루틴을 한 칸 위(up=true)나 아래로 옮깁니다. */
    onMoveRoutine: (RoutineEntity, Boolean) -> Unit,
    /** 루틴을 그 자리(0부터)로 한 번에 옮깁니다. */
    onMoveRoutineTo: (RoutineEntity, Int) -> Unit,
    modifier: Modifier = Modifier,
    /** 검색에서 고른 루틴. 그 자리로 굴려 주고 잠깐 강조합니다. */
    focusId: Long? = null
) {
    var editingRoutineId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showDeleteRoutineId by rememberSaveable { mutableStateOf<Long?>(null) }
    var memoRoutineId by rememberSaveable { mutableStateOf<Long?>(null) }
    var movingRoutineId by rememberSaveable { mutableStateOf<Long?>(null) }
    val editingRoutine = routines.firstOrNull { it.id == editingRoutineId }
    val deletingRoutine = routines.firstOrNull { it.id == showDeleteRoutineId }
    val memoRoutine = routines.firstOrNull { it.id == memoRoutineId }
    val movingRoutine = routines.firstOrNull { it.id == movingRoutineId }
    val memosByRoutine = memos.groupBy { it.routineId }
    // 하루를 밟는 차례입니다. 수행한 것을 아래로 내리지 않습니다. 자리가 움직이면 어디까지 했는지
    // 눈으로 짚기 어렵고, 사용자가 정한 순서라는 약속도 깨집니다.
    val sortedRoutines = RoutineOrder.sorted(routines)
    // 위에서부터 아직 오늘 하지 않은 첫 루틴이 지금 할 차례입니다.
    val nextRoutine = sortedRoutines.firstOrNull { (todayCounts[it.id] ?: 0) == 0 }
    val nextRoutineId = nextRoutine?.id
    val doneCount = sortedRoutines.count { (todayCounts[it.id] ?: 0) > 0 }

    var selectedFilter by rememberSaveable { mutableStateOf(RoutineFilter.ALL.name) }
    val currentFilter = RoutineFilter.valueOf(selectedFilter)
    val rows = remember(sortedRoutines, todayCounts, currentFilter) {
        sortedRoutines
            .mapIndexed { index, routine ->
                RoutineRow(
                    routine = routine,
                    step = index + 1,
                    canMoveUp = index > 0,
                    canMoveDown = index < sortedRoutines.lastIndex
                )
            }
            .filter { row -> currentFilter.matches(todayCounts[row.routine.id] ?: 0) }
    }

    // 검색에서 고른 루틴이 지금 거르개에 걸려 안 보이면, 찾아 놓고도 빈 화면을 보게 됩니다.
    LaunchedEffect(focusId, currentFilter, sortedRoutines) {
        if (focusId != null && sortedRoutines.any { it.id == focusId } && rows.none { it.routine.id == focusId }) {
            selectedFilter = RoutineFilter.ALL.name
        }
    }

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

    if (movingRoutine != null) {
        RoutineMoveDialog(
            routine = movingRoutine,
            ordered = sortedRoutines,
            onDismiss = { movingRoutineId = null },
            onMoveTo = { targetIndex ->
                onMoveRoutineTo(movingRoutine, targetIndex)
                movingRoutineId = null
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
    // 추가 버튼·진행 카드·거르개를 지나야 루틴 목록이 시작합니다.
    val focusIndex = remember(focusId, rows) {
        rows.indexOfFirst { it.routine.id == focusId }.takeIf { it >= 0 }?.plus(HEADER_ITEM_COUNT)
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
                            text = "반복해서 수행할 일을 추가하면 오늘 수행 횟수를 누적할 수 있습니다. " +
                                "추가한 순서대로 위에서 아래로 밟고, 화살표로 차례를 바꿀 수 있습니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            item {
                RoutineProgressCard(
                    doneCount = doneCount,
                    totalCount = sortedRoutines.size,
                    nextRoutine = nextRoutine
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(RoutineFilter.entries) { filter ->
                        CountFilterPill(
                            title = filter.label,
                            count = when (filter) {
                                RoutineFilter.ALL -> sortedRoutines.size
                                RoutineFilter.UNDONE -> sortedRoutines.size - doneCount
                                RoutineFilter.DONE -> doneCount
                            },
                            selected = currentFilter == filter,
                            onClick = { selectedFilter = filter.name },
                            modifier = Modifier.testTag(routineFilterTag(filter.name))
                        )
                    }
                }
            }

            if (rows.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (currentFilter == RoutineFilter.UNDONE) {
                                    "오늘 루틴을 모두 밟았습니다."
                                } else {
                                    "아직 밟은 루틴이 없습니다."
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "전체를 누르면 다시 다 보입니다.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(rows, key = { row -> row.routine.id }) { row ->
                    RoutineCard(
                        routine = row.routine,
                        step = row.step,
                        isNext = row.routine.id == nextRoutineId,
                        canMoveUp = row.canMoveUp,
                        canMoveDown = row.canMoveDown,
                        focused = row.routine.id == focusId,
                        focusKey = focusId,
                        todayCount = todayCounts[row.routine.id] ?: 0,
                        memoCount = memosByRoutine[row.routine.id]?.size ?: 0,
                        latestMemo = memosByRoutine[row.routine.id]?.maxByOrNull { it.createdAt },
                        onOpenMemos = { memoRoutineId = row.routine.id },
                        onCheckRoutine = onCheckRoutine,
                        onMoveRoutine = onMoveRoutine,
                        onOpenMove = { movingRoutineId = row.routine.id },
                        onEditRoutine = { editingRoutineId = row.routine.id },
                        onDeleteRoutine = { showDeleteRoutineId = row.routine.id }
                    )
                }
            }
        }
    }
}

/**
 * 오늘 어디까지 왔는지.
 *
 * 개수만 적으면 "여덟 개 중 셋"이 얼마만큼인지 머리로 셈해야 합니다. 막대를 함께 둡니다.
 * 다 끝낸 날에는 남은 것을 세지 않고 끝났다고만 말합니다. 다그치지 않는 것이 이 앱의 규칙입니다.
 */
@Composable
private fun RoutineProgressCard(
    doneCount: Int,
    totalCount: Int,
    nextRoutine: RoutineEntity?
) {
    val ratio = if (totalCount > 0) doneCount.toFloat() / totalCount else 0f
    val animatedRatio by animateFloatAsState(targetValue = ratio, label = "routineProgress")
    val percent = (ratio * 100).roundToInt()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "오늘 진행",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$doneCount / $totalCount · $percent%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            LinearProgressIndicator(
                progress = { animatedRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(6.dp))
            )

            Text(
                text = when {
                    totalCount == 0 -> "루틴을 추가하면 여기에 오늘 진행이 보입니다."
                    nextRoutine == null -> "오늘 몫을 다 밟았습니다."
                    else -> "다음 차례: ${nextRoutine.title}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun RoutineCalendarCard(
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
    /** 화면에 보이는 차례. 1부터 셉니다. */
    step: Int,
    /** 오늘 아직 하지 않은 것 중 맨 위인지. */
    isNext: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    todayCount: Int,
    memoCount: Int,
    latestMemo: RoutineMemoEntity?,
    onOpenMemos: () -> Unit,
    onCheckRoutine: (RoutineEntity) -> Unit,
    onMoveRoutine: (RoutineEntity, Boolean) -> Unit,
    /** 차례를 한 번에 옮기는 대화상자를 엽니다. */
    onOpenMove: () -> Unit,
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
                RoutineOrderControls(
                    step = step,
                    canMoveUp = canMoveUp,
                    canMoveDown = canMoveDown,
                    onMoveUp = { onMoveRoutine(routine, true) },
                    onMoveDown = { onMoveRoutine(routine, false) },
                    onOpenMove = onOpenMove
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isNext) {
                        Text(
                            text = "다음 차례",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
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
                    if (routine.sourceContentSyncId.isNotBlank()) {
                        // 어디서 비롯됐는지 적어 둡니다. 위의 메모가 그 글귀 본문입니다.
                        Text(
                            text = "글귀에서 뽑음",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
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

/**
 * 차례 번호와 위·아래 화살표.
 *
 * 번호를 화살표 사이에 두어, 어느 쪽을 누르면 몇 번째가 되는지 눈으로 바로 잡히게 합니다.
 * 맨 위·맨 아래에서는 해당 화살표를 꺼 두어 눌러도 아무 일이 없는 상태를 만들지 않습니다.
 *
 * 번호 자체는 누를 수 있습니다. 화살표는 한 칸씩이라, 서른 번째를 맨 위로 올리려면
 * 스물아홉 번을 눌러야 하기 때문입니다.
 */
@Composable
private fun RoutineOrderControls(
    step: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onOpenMove: () -> Unit
) {
    // 화살표와 번호를 붙여 두어야 셋이 한 덩어리로 보입니다. 사이를 벌리면 번호가 따로 떠 보입니다.
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onMoveUp,
            enabled = canMoveUp,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowUp,
                contentDescription = "$step 번째 루틴을 앞으로"
            )
        }
        // 눌러 볼 만한 것으로 보이도록 옅은 알약을 깔았습니다. 맨 숫자는 그냥 표시로 읽힙니다.
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(onClick = onOpenMove)
                .semantics { contentDescription = "$step 번째. 차례 옮기기" }
        ) {
            Text(
                text = step.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
            )
        }
        IconButton(
            onClick = onMoveDown,
            enabled = canMoveDown,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = "$step 번째 루틴을 뒤로"
            )
        }
    }
}

/**
 * 차례를 한 번에 옮기는 대화상자.
 *
 * 화살표만으로는 먼 자리로 가기 어렵습니다. 맨 위·맨 아래는 버튼 하나로,
 * 그 사이는 지금 줄에서 자리를 짚어 고릅니다. 몇 번째가 될지 이름을 보며 고를 수 있어
 * 숫자만 적어 넣는 것보다 헷갈리지 않습니다.
 */
@Composable
private fun RoutineMoveDialog(
    routine: RoutineEntity,
    ordered: List<RoutineEntity>,
    onDismiss: () -> Unit,
    onMoveTo: (Int) -> Unit
) {
    val currentIndex = ordered.indexOfFirst { it.id == routine.id }
    // 지금 자리가 보이는 곳에서 목록을 엽니다. 서른 개가 넘으면 어디였는지 찾기부터 해야 합니다.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (currentIndex - 2).coerceAtLeast(0)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("차례 옮기기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = routine.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onMoveTo(0) },
                        enabled = currentIndex > 0,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("맨 위로")
                    }
                    OutlinedButton(
                        onClick = { onMoveTo(RoutineOrder.LAST_INDEX) },
                        enabled = currentIndex >= 0 && currentIndex < ordered.lastIndex,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("맨 아래로")
                    }
                }
                Text(
                    text = "또는 옮길 자리를 누르세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    itemsIndexed(ordered, key = { _, item -> item.id }) { index, item ->
                        RoutineMoveTargetRow(
                            step = index + 1,
                            title = item.title,
                            isCurrent = item.id == routine.id,
                            onClick = { onMoveTo(index) }
                        )
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
private fun RoutineMoveTargetRow(
    step: Int,
    title: String,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(routineMoveTargetTag(step))
            // 지금 자리를 눌러도 달라지는 것이 없어 눌리지 않게 둡니다.
            .clickable(enabled = !isCurrent, onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = step.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (isCurrent) {
            Text(
                text = "지금 자리",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
