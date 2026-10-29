package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ContentMemoEntity
import com.codex.appgoodwords.data.ContentType
import com.codex.appgoodwords.data.GoalTrack
import com.codex.appgoodwords.data.HomeGoalLine
import com.codex.appgoodwords.data.RoutineEntity
import com.codex.appgoodwords.data.RoutineOrder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

internal fun goalTabTag(tab: GoalTab): String = "goal_tab_" + tab.name.lowercase()
internal fun goalAddTag(tab: GoalTab): String = "goal_add_" + tab.name.lowercase()
internal fun goalCardTag(id: Long): String = "goal_card_$id"
internal const val goalTitleFieldTag = "goal_title_field"
internal const val goalBodyFieldTag = "goal_body_field"
internal const val goalSaveTag = "goal_save"
internal const val goalReflectFieldTag = "goal_reflect_field"
internal const val goalReflectSaveTag = "goal_reflect_save"
internal const val goalLinkRoutinesTag = "goal_link_routines"
internal const val goalPinTag = "goal_pin"
internal const val goalEditTag = "goal_edit"
internal const val goalDeleteTag = "goal_delete"
internal const val goalConfirmDeleteTag = "goal_confirm_delete"
internal fun goalRoutineCheckTag(syncId: String): String = "goal_routine_check_$syncId"
internal const val goalRoutinesSaveTag = "goal_routines_save"
internal const val homeGoalLineTag = "home_goal_line"
internal const val goalDetailListTag = "goal_detail_list"

/**
 * 목표 탭의 세 칸 — 되고 싶은 나, 이루고 싶은 것, 닮고 싶은 사람(사용자가 정함, 2026-10-29).
 *
 * 할 일이나 메모와 다릅니다. 끝내거나 적어 두고 마는 것이 아니라 **자꾸 마주치고 되새기는 것**이라, 무엇을 적느냐보다
 * 다시 들여다보는 길(되새기기)과 실제로 그쪽으로 움직였는지(이어 둔 루틴)가 화면의 중심입니다.
 */
enum class GoalTab(
    val type: ContentType,
    val label: String,
    /** 칸 맨 위의 안내. 무엇을 적는 자리인지 한 줄로. */
    val intro: String,
    /** 적는 칸의 이름과 예. */
    val titleLabel: String,
    val titlePlaceholder: String,
    val bodyLabel: String,
    val bodyPlaceholder: String,
    /** 되새길 때 던지는 물음. */
    val reflectPrompt: String,
    val emptyBody: String
) {
    VISION(
        type = ContentType.VISION,
        label = "이상향",
        intro = "되고 싶은 나를 한 문장으로. 매일 마주치며 오늘의 나와 견줘 봅니다.",
        titleLabel = "되고 싶은 모습",
        titlePlaceholder = "예: 아침을 지배하는 사람",
        bodyLabel = "그 모습은 어떤가요 (선택)",
        bodyPlaceholder = "예: 일어나자마자 휴대폰 대신 물 한 컵과 산책으로 하루를 연다",
        reflectPrompt = "오늘 이 모습에 가까웠던 순간이 있었나요?",
        emptyBody = "되고 싶은 나를 한 문장으로 적어 두면, 홈에서 매일 마주칩니다."
    ),
    GOAL(
        type = ContentType.GOAL,
        label = "목표",
        intro = "이루고 싶은 것. 기한이나 숫자로 재지 않고, 되새긴 기록과 이어 둔 루틴으로 따라갑니다.",
        titleLabel = "목표",
        titlePlaceholder = "예: 올해 하프 마라톤 완주",
        bodyLabel = "왜 이루고 싶은가요 (선택)",
        bodyPlaceholder = "예: 무너지지 않는 체력이 있어야 다른 것도 오래 할 수 있어서",
        reflectPrompt = "지금 어디쯤 와 있나요? 다음 한 걸음은 무엇인가요?",
        emptyBody = "이루고 싶은 것을 하나 적고, 그것을 위해 하는 루틴을 이어 두세요."
    ),
    ROLE_MODEL(
        type = ContentType.ROLE_MODEL,
        label = "롤모델",
        intro = "닮고 싶은 사람. 보관함에서 저자가 같은 글귀가 여기에 모입니다.",
        titleLabel = "이름",
        titlePlaceholder = "예: James Clear",
        bodyLabel = "닮고 싶은 점 (선택)",
        bodyPlaceholder = "예: 거창한 결심 대신 작은 습관을 쌓는 꾸준함",
        reflectPrompt = "그라면 오늘 어떻게 했을까요?",
        emptyBody = "닮고 싶은 사람을 적어 두면, 그 사람의 글귀와 내가 배운 것이 한자리에 쌓입니다."
    );

    companion object {
        fun of(type: ContentType): GoalTab = entries.firstOrNull { it.type == type } ?: GOAL
    }
}

/** 목표 탭. 세 칸을 나란히 두고 칸마다 보던 자리를 따로 기억합니다([RoutineTabsScreen]과 같은 방식). */
@Composable
fun GoalTabsScreen(
    tracks: List<GoalTrack>,
    onOpen: (Long) -> Unit,
    onSave: suspend (id: Long, type: ContentType, title: String, body: String) -> Result<Long>,
    modifier: Modifier = Modifier
) {
    var selected by rememberSaveable { mutableStateOf(GoalTab.VISION) }
    var adding by rememberSaveable { mutableStateOf<GoalTab?>(null) }
    // 막 적은 것. 적는 창이 닫힌 뒤에 그 기록으로 들어갑니다 — 창이 열린 채로 넘어가면 이 화면의 상태가 창과 함께
    // 저장되어, 돌아왔을 때 창이 다시 뜨고 다음에 여는 창에 앞서 친 글이 남아 있었습니다.
    var justSaved by remember { mutableStateOf<Long?>(null) }
    val stateHolder = rememberSaveableStateHolder()

    LaunchedEffect(justSaved, adding) {
        val id = justSaved ?: return@LaunchedEffect
        if (adding != null) return@LaunchedEffect
        justSaved = null
        onOpen(id)
    }

    adding?.let { tab ->
        GoalEditorDialog(
            tab = tab,
            initialTitle = "",
            initialBody = "",
            onSave = { title, body ->
                onSave(0L, tab.type, title, body).map { id -> justSaved = id }
            },
            onDismiss = { adding = null }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selected.ordinal) {
            GoalTab.entries.forEach { tab ->
                val count = tracks.count { it.goal.type == tab.type }
                Tab(
                    selected = selected == tab,
                    onClick = { selected = tab },
                    text = { Text(if (count > 0) "${tab.label} $count" else tab.label) },
                    modifier = Modifier.testTag(goalTabTag(tab))
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            stateHolder.SaveableStateProvider(selected) {
                GoalList(
                    tab = selected,
                    tracks = tracks.filter { it.goal.type == selected.type }
                        // 홈에 두기를 고른 것이 먼저, 그다음 적은 차례입니다.
                        .sortedWith(compareByDescending<GoalTrack> { it.goal.isFavorite }.thenBy { it.goal.createdAt }),
                    onAdd = { adding = selected },
                    onOpen = onOpen
                )
            }
        }
    }
}

@Composable
private fun GoalList(
    tab: GoalTab,
    tracks: List<GoalTrack>,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "intro") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = tab.intro,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().testTag(goalAddTag(tab))) {
                    Text("${tab.label} 적기")
                }
            }
        }
        if (tracks.isEmpty()) {
            item(key = "empty") { EmptyCard(title = "아직 적어 둔 것이 없습니다", body = tab.emptyBody) }
        }
        items(tracks, key = { it.goal.id }) { track ->
            GoalCard(track = track, onOpen = { onOpen(track.goal.id) })
        }
    }
}

/** 목록의 카드 한 장. 무엇을 적었는지와, 그쪽으로 얼마나 다가가고 있는지를 한 줄씩. */
@Composable
private fun GoalCard(track: GoalTrack, onOpen: () -> Unit) {
    val goal = track.goal
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onOpen)
            .testTag(goalCardTag(goal.id)),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = goal.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (goal.isFavorite) {
                    Icon(
                        Icons.Outlined.PushPin,
                        contentDescription = "홈에 둠",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (goal.body.isNotBlank()) {
                Text(
                    text = goal.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            GoalPulse(track = track)
        }
    }
}

/**
 * 따라가고 있는 모습 한 줄 — 되새김, 이어 둔 루틴, 롤모델의 글귀.
 *
 * 못 한 것은 세지 않습니다. "마지막 되새김 이후 n일" 대신 언제였는지만, "빠진 날" 대신 움직인 날만 적습니다.
 */
@Composable
private fun GoalPulse(track: GoalTrack) {
    val parts = buildList {
        add(
            track.lastReflectedAt?.let { "되새김 ${track.reflections.size}번 · 마지막 ${formatDay(it)}" }
                ?: "아직 되새긴 적 없어요"
        )
        if (track.routines.isNotEmpty()) {
            add("루틴 ${track.routines.size}개 · 최근 4주에 ${track.recentActiveDays}일 움직임")
        }
        if (track.quotes.isNotEmpty()) add("글귀 ${track.quotes.size}개")
    }
    Text(
        text = parts.joinToString("  ·  "),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

/**
 * 이상향·목표·롤모델 하나.
 *
 * 맨 위에 무엇인지, 바로 아래 되새기는 칸 — 이 화면에 들어오는 가장 흔한 까닭이 되새기기라서입니다. 그 아래 이어 둔
 * 루틴(실제로 그쪽으로 움직였는지), 롤모델이면 그 사람의 글귀, 맨 아래 쌓인 되새김입니다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalDetailScreen(
    track: GoalTrack,
    routines: List<RoutineEntity>,
    onSaveText: suspend (title: String, body: String) -> Result<Long>,
    onSetRoutines: suspend (List<String>) -> Result<Unit>,
    onSetPinned: suspend (Boolean) -> Result<Unit>,
    onDelete: suspend () -> Result<Unit>,
    onReflect: suspend (String) -> Result<Long>,
    onDeleteReflection: suspend (Long) -> Result<Int>,
    onOpenQuote: (ContentItemEntity) -> Unit,
    onOpenRoutines: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val goal = track.goal
    val tab = GoalTab.of(goal.type)
    val scope = rememberCoroutineScope()
    var editing by rememberSaveable { mutableStateOf(false) }
    var linking by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var reflection by rememberSaveable(goal.id) { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    if (editing) {
        GoalEditorDialog(
            tab = tab,
            initialTitle = goal.title,
            initialBody = goal.body,
            onSave = { title, body -> onSaveText(title, body).map { } },
            onDismiss = { editing = false }
        )
    }
    if (linking) {
        RoutinePickerDialog(
            routines = RoutineOrder.sorted(routines),
            selected = goal.linkedRoutineSyncIds.toSet(),
            onSave = { chosen -> onSetRoutines(chosen) },
            onDismiss = { linking = false }
        )
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("${tab.label} 지우기") },
            text = { Text("'${goal.title}' — 되새긴 기록 ${track.reflections.size}개까지 함께 지울까요? 이어 둔 루틴은 그대로 남습니다.") },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag(goalConfirmDeleteTag),
                    onClick = {
                        confirmingDelete = false
                        scope.launch {
                            onDelete().onFailure { onMessage(it.message ?: "지우지 못했습니다.") }
                        }
                    }
                ) { Text("지우기") }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("취소") } }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(goalDetailListTag),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "head") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tab.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(goal.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                    if (goal.body.isNotBlank()) {
                        Text(goal.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { editing = true }, modifier = Modifier.testTag(goalEditTag)) { Text("고치기") }
                        TextButton(
                            onClick = {
                                scope.launch {
                                    onSetPinned(!goal.isFavorite)
                                        .onSuccess { onMessage(if (goal.isFavorite) "홈에서 내렸습니다." else "홈 '오늘' 칸 맨 위에 둡니다.") }
                                        .onFailure { onMessage(it.message ?: "바꾸지 못했습니다.") }
                                }
                            },
                            modifier = Modifier.testTag(goalPinTag)
                        ) { Text(if (goal.isFavorite) "홈에서 내리기" else "홈에 두기") }
                        TextButton(onClick = { confirmingDelete = true }, modifier = Modifier.testTag(goalDeleteTag)) {
                            Text("지우기", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // 되새기는 칸. 이 화면에 들어오는 가장 흔한 까닭이라 맨 위 가까이에 둡니다.
        item(key = "reflect") {
            DetailSection(title = "되새기기") {
                Text(tab.reflectPrompt, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = reflection,
                    onValueChange = { reflection = it },
                    placeholder = { Text("한 줄이면 충분합니다") },
                    minLines = 2,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().testTag(goalReflectFieldTag)
                )
                Button(
                    onClick = {
                        saving = true
                        scope.launch {
                            onReflect(reflection.trim())
                                .onSuccess { reflection = ""; onMessage("되새긴 것을 남겼습니다.") }
                                .onFailure { onMessage(it.message ?: "남기지 못했습니다.") }
                            saving = false
                        }
                    },
                    enabled = reflection.isNotBlank() && !saving,
                    modifier = Modifier.testTag(goalReflectSaveTag)
                ) { Text(if (saving) "남기는 중…" else "남기기") }
            }
        }

        item(key = "routines") {
            DetailSection(title = "이어 둔 루틴") {
                if (track.routines.isEmpty()) {
                    Text(
                        text = "이것을 위해 하는 루틴을 이어 두면, 실제로 그쪽으로 움직인 날이 여기 쌓입니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "최근 4주에 ${track.recentActiveDays}일 이쪽으로 움직였습니다.",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    track.routines.forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable(onClick = onOpenRoutines)
                                .heightIn(min = 44.dp)
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(row.routine.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text(
                                text = buildString {
                                    append("최근 4주 ${row.recentDays}일")
                                    if (row.currentStreakDays > 1) append(" · ${row.currentStreakDays}일째")
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                FilledTonalButton(onClick = { linking = true }, modifier = Modifier.testTag(goalLinkRoutinesTag)) {
                    Text(if (track.routines.isEmpty()) "루틴 잇기" else "이어 둔 루틴 바꾸기")
                }
            }
        }

        if (goal.type == ContentType.ROLE_MODEL) {
            item(key = "quotes") {
                DetailSection(title = "${goal.title}의 글귀") {
                    if (track.quotes.isEmpty()) {
                        Text(
                            text = "보관함에 저자를 '${goal.title}'(으)로 담은 글귀가 여기에 모입니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        track.quotes.take(MAX_QUOTES).forEach { quote ->
                            Text(
                                text = "“${quote.body.ifBlank { quote.title }}”",
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable { onOpenQuote(quote) }
                                    .padding(vertical = 6.dp)
                            )
                        }
                        if (track.quotes.size > MAX_QUOTES) {
                            Text(
                                "보관함에 ${track.quotes.size - MAX_QUOTES}개 더 있습니다.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item(key = "history-title") {
            Text(
                text = if (track.reflections.isEmpty()) "되새긴 기록" else "되새긴 기록 ${track.reflections.size}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        if (track.reflections.isEmpty()) {
            item(key = "history-empty") {
                Text(
                    text = "남긴 것이 여기에 날짜와 함께 쌓입니다. 지난 기록을 다시 읽는 것도 되새기기입니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(track.reflections, key = { "memo-${it.id}" }) { memo ->
            ReflectionRow(
                memo = memo,
                onDelete = {
                    scope.launch {
                        onDeleteReflection(memo.id).onFailure { onMessage(it.message ?: "지우지 못했습니다.") }
                    }
                }
            )
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun ReflectionRow(memo: ContentMemoEntity, onDelete: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .padding(start = 18.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(formatDay(memo.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(memo.body, style = MaterialTheme.typography.bodyLarge)
        }
        if (confirming) {
            TextButton(onClick = { confirming = false; onDelete() }) {
                Text("정말 지우기", color = MaterialTheme.colorScheme.error)
            }
        } else {
            TextButton(onClick = { confirming = true }) { Text("지우기", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** 적거나 고치는 칸. 제목만 있으면 됩니다. */
@Composable
private fun GoalEditorDialog(
    tab: GoalTab,
    initialTitle: String,
    initialBody: String,
    onSave: suspend (title: String, body: String) -> Result<Unit>,
    onDismiss: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var body by rememberSaveable { mutableStateOf(initialBody) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (initialTitle.isBlank()) "${tab.label} 적기" else "${tab.label} 고치기") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(tab.titleLabel) },
                    placeholder = { Text(tab.titlePlaceholder) },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().testTag(goalTitleFieldTag)
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text(tab.bodyLabel) },
                    placeholder = { Text(tab.bodyPlaceholder) },
                    minLines = 3,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().testTag(goalBodyFieldTag)
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && !saving,
                modifier = Modifier.testTag(goalSaveTag),
                onClick = {
                    saving = true
                    error = null
                    scope.launch {
                        val result = onSave(title.trim(), body.trim())
                        saving = false
                        if (result.isSuccess) onDismiss()
                        else error = result.exceptionOrNull()?.message ?: "저장하지 못했습니다. 다시 시도해 주세요."
                    }
                }
            ) { Text(if (saving) "저장 중…" else "저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("취소") } }
    )
}

/** 이어 둘 루틴 고르기. 루틴 차례대로 늘어놓습니다. */
@Composable
private fun RoutinePickerDialog(
    routines: List<RoutineEntity>,
    selected: Set<String>,
    onSave: suspend (List<String>) -> Result<Unit>,
    onDismiss: () -> Unit
) {
    var chosen by remember { mutableStateOf(selected.filter { id -> routines.any { it.syncId == id } }.toSet()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("루틴 잇기") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (routines.isEmpty()) {
                    Text("아직 루틴이 없습니다. 루틴 탭에서 먼저 만들어 주세요.", style = MaterialTheme.typography.bodyMedium)
                }
                routines.forEach { routine ->
                    val checked = routine.syncId in chosen
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { chosen = if (checked) chosen - routine.syncId else chosen + routine.syncId }
                            .testTag(goalRoutineCheckTag(routine.syncId)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(routine.title, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                modifier = Modifier.testTag(goalRoutinesSaveTag),
                onClick = {
                    saving = true
                    scope.launch {
                        // 고른 차례가 아니라 루틴 차례로 남깁니다. 카드의 루틴 줄이 루틴 탭과 같은 차례로 보입니다.
                        val result = onSave(routines.map { it.syncId }.filter { it in chosen })
                        saving = false
                        if (result.isSuccess) onDismiss()
                        else error = result.exceptionOrNull()?.message ?: "저장하지 못했습니다."
                    }
                }
            ) { Text(if (saving) "저장 중…" else "저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("취소") } }
    )
}

/**
 * 홈 "오늘" 칸 맨 위의 한 줄 — 되고 싶은 나와, 목표나 롤모델 하나.
 *
 * 매일 마주치게 하려는 자리라 한두 줄로 가볍게 둡니다. 숫자나 해야 할 일을 붙이지 않습니다 — 여기가 성적표가 되면
 * 홈을 열기 싫어집니다. 누르면 그 기록으로 갑니다.
 */
@Composable
fun HomeGoalCard(line: HomeGoalLine, onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().testTag(homeGoalLineTag),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            listOfNotNull(line.vision, line.other).forEach { goal ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onOpen(goal.id) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Outlined.Explore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (goal.type) {
                                ContentType.VISION -> "되고 싶은 나"
                                ContentType.ROLE_MODEL -> "닮고 싶은 사람"
                                else -> "이루고 싶은 것"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = goal.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun formatDay(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN))

/** 롤모델 화면에 다 펼쳐 둘 글귀 수. 나머지는 보관함에서 봅니다. */
private const val MAX_QUOTES = 5
