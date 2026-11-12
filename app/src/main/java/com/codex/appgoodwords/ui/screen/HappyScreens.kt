package com.codex.appgoodwords.ui.screen

import android.app.DatePickerDialog
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.codex.appgoodwords.data.Connections
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ContentType
import com.codex.appgoodwords.data.DayDigest
import com.codex.appgoodwords.data.Happy
import com.codex.appgoodwords.data.LinkedRecord
import com.codex.appgoodwords.data.RecordRef
import com.codex.appgoodwords.data.Walking
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

internal const val happyAddMomentTag = "happy_add_moment"
internal const val happyAddLikeTag = "happy_add_like"
internal fun happyMomentCardTag(id: Long): String = "happy_moment_card_$id"
internal fun happyLikeCardTag(id: Long): String = "happy_like_card_$id"
internal const val momentTextFieldTag = "happy_moment_text"
internal const val momentDateTodayTag = "happy_moment_date_today"
internal const val momentDateYesterdayTag = "happy_moment_date_yesterday"
internal const val momentDatePickTag = "happy_moment_date_pick"
internal fun momentLikeChipTag(syncId: String): String = "happy_moment_like_$syncId"
internal const val momentNewLikeTag = "happy_moment_new_like"
internal const val momentNewLikeFieldTag = "happy_moment_new_like_field"
internal const val momentNewLikeAddTag = "happy_moment_new_like_add"
internal const val momentSaveTag = "happy_moment_save"
internal const val happyAddPhotoTag = "happy_add_photo"
internal const val likeNameFieldTag = "happy_like_name"
internal fun likeCategoryChipTag(category: String): String = "happy_like_category_$category"
internal const val likeCustomCategoryTag = "happy_like_category_custom"
internal const val likeCustomCategoryFieldTag = "happy_like_category_field"
internal const val likeWhyFieldTag = "happy_like_why"
internal const val likeSaveTag = "happy_like_save"
internal const val happyDetailListTag = "happy_detail_list"
internal const val happyEditTag = "happy_edit"
internal const val happyDeleteTag = "happy_delete"
internal const val happyConfirmDeleteTag = "happy_confirm_delete"
internal const val happyLinkTag = "happy_link"
internal const val happyLinkSearchFieldTag = "happy_link_search"
internal fun happyLinkResultTag(syncId: String): String = "happy_link_result_$syncId"
internal fun happyLinkedRowTag(syncId: String): String = "happy_linked_$syncId"
internal fun happyUnlinkTag(syncId: String): String = "happy_unlink_$syncId"
internal fun happyMentionRowTag(syncId: String): String = "happy_mention_$syncId"
internal fun happyPromoteTag(syncId: String): String = "happy_promote_$syncId"
internal const val happySameDayTag = "happy_same_day"
internal const val happyDayOpenTag = "happy_day_open"

/**
 * 행복한 순간 목록 — 보관함 "행복" 칸의 첫 알약(사용자가 정함, 2026-11-12, 목업으로 고름).
 *
 * 날짜별로 묶어 최근 것이 위입니다. 카드에는 글과 이어 둔 좋아하는 것("♡ 노을 · 산책"), 사진이 있으면 첫 장을 둡니다.
 * 적고 나면 목록에 머뭅니다 — 순간은 떠오른 김에 여러 개 적는 일이 많습니다.
 */
@Composable
fun HappyMomentList(
    moments: List<ContentItemEntity>,
    likes: List<ContentItemEntity>,
    likesByMoment: Map<String, List<ContentItemEntity>>,
    onOpen: (Long) -> Unit,
    onSave: suspend (text: String, date: LocalDate, imageUris: List<String>, likeSyncIds: List<String>) -> Result<Long>,
    onCreateLike: suspend (name: String) -> Result<Long>,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    serverUrl: String = "",
    apiKey: String = ""
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    if (adding) {
        HappyMomentEditorDialog(
            title = "행복한 순간 적기",
            initialText = "",
            initialDate = LocalDate.now(),
            initialImages = emptyList(),
            initialLikeSyncIds = emptyList(),
            likes = likes,
            onSave = { text, date, images, likeIds ->
                onSave(text, date, images, likeIds).map { onMessage("행복한 순간을 남겼습니다.") }
            },
            onCreateLike = onCreateLike,
            onDismiss = { adding = false },
            serverUrl = serverUrl,
            apiKey = apiKey
        )
    }
    val byDate = remember(moments) {
        val zone = ZoneId.systemDefault()
        moments.sortedByDescending { it.createdAt }.groupBy { Happy.dateOf(it.createdAt, zone) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "intro") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "행복했던 순간을 한 줄로. 그때 있던 좋아하는 것을 이어 두면, 무엇이 나를 행복하게 하는지 쌓여 보입니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().testTag(happyAddMomentTag)) {
                    Text("행복한 순간 적기")
                }
            }
        }
        if (moments.isEmpty()) {
            item(key = "empty") {
                EmptyCard(
                    title = "아직 적어 둔 순간이 없습니다",
                    body = "작은 것도 좋습니다. 오늘 웃었던 순간을 하나 남겨 보세요."
                )
            }
        }
        byDate.forEach { (date, dayMoments) ->
            item(key = "day-$date") {
                Text(
                    text = formatHappyDate(date),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            items(dayMoments, key = { it.id }) { moment ->
                MomentCard(
                    moment = moment,
                    likes = likesByMoment[moment.syncId].orEmpty(),
                    onOpen = { onOpen(moment.id) },
                    serverUrl = serverUrl,
                    apiKey = apiKey
                )
            }
        }
    }
}

@Composable
private fun MomentCard(
    moment: ContentItemEntity,
    likes: List<ContentItemEntity>,
    onOpen: () -> Unit,
    serverUrl: String,
    apiKey: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onOpen)
            .testTag(happyMomentCardTag(moment.id)),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = moment.body.ifBlank { moment.title },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (likes.isNotEmpty()) {
                    Text(
                        text = "♡ " + likes.joinToString(" · ") { it.title },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            moment.imageUris.firstOrNull()?.let { uri ->
                Spacer(modifier = Modifier.width(12.dp))
                HappyThumbnail(uri = uri, serverUrl = serverUrl, apiKey = apiKey, size = 64.dp)
            }
        }
    }
}

/**
 * 좋아하는 것 목록 — 보관함 "행복" 칸의 둘째 알약. 갈래별로 묶고(정해 둔 갈래 차례, 갈래 없음은 맨 뒤), 카드에 몇 순간에
 * 있었는지 보입니다(이어 두었거나 순간의 글에 이름이 나온 것 — [Happy.momentCounts]).
 */
@Composable
fun LikeList(
    likes: List<ContentItemEntity>,
    momentCounts: Map<String, Int>,
    onOpen: (Long) -> Unit,
    onSave: suspend (name: String, category: String, why: String, imageUris: List<String>) -> Result<Long>,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    serverUrl: String = "",
    apiKey: String = ""
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    if (adding) {
        LikeEditorDialog(
            title = "좋아하는 것 적기",
            initialName = "",
            initialCategory = "",
            initialWhy = "",
            initialImages = emptyList(),
            onSave = { name, category, why, images ->
                onSave(name, category, why, images).map { onMessage("좋아하는 것에 담았습니다.") }
            },
            onDismiss = { adding = false },
            serverUrl = serverUrl,
            apiKey = apiKey
        )
    }
    val groups = remember(likes) { Happy.groupLikes(likes) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "intro") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "내가 좋아하는 것 — 음식·음악·장소·사람·활동… 행복한 순간을 적을 때 칩으로 이어 둘 수 있습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().testTag(happyAddLikeTag)) {
                    Text("좋아하는 것 적기")
                }
            }
        }
        if (likes.isEmpty()) {
            item(key = "empty") {
                EmptyCard(
                    title = "아직 적어 둔 것이 없습니다",
                    body = "좋아하는 것을 하나 적어 두면, 그것이 있던 순간들이 여기에 모입니다."
                )
            }
        }
        groups.forEach { (category, group) ->
            item(key = "category-$category") {
                Text(
                    text = category.ifBlank { "갈래 없음" },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            items(group, key = { it.id }) { like ->
                LikeCard(
                    like = like,
                    momentCount = momentCounts[like.syncId] ?: 0,
                    onOpen = { onOpen(like.id) },
                    serverUrl = serverUrl,
                    apiKey = apiKey
                )
            }
        }
    }
}

@Composable
private fun LikeCard(like: ContentItemEntity, momentCount: Int, onOpen: () -> Unit, serverUrl: String, apiKey: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onOpen)
            .testTag(happyLikeCardTag(like.id)),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(like.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (like.body.isNotBlank()) {
                    Text(
                        text = like.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // 있었던 순간만 셉니다. 없으면 아무 말도 하지 않습니다(못 한 것을 세지 않습니다).
                if (momentCount > 0) {
                    Text(
                        text = "행복한 순간 ${momentCount}개",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            like.imageUris.firstOrNull()?.let { uri ->
                Spacer(modifier = Modifier.width(12.dp))
                HappyThumbnail(uri = uri, serverUrl = serverUrl, apiKey = apiKey, size = 56.dp)
            }
        }
    }
}

/**
 * 행복한 순간·좋아하는 것 하나의 화면. 맨 위에 무엇인지, 그 아래 **이어진 기록**(사용자가 정함, 2026-11-12 — 직접 이은 것 +
 * 저절로 보이는 연결), 순간이면 **같은 날**의 기록(그날 요약과 같은 셈)입니다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HappyDetailScreen(
    item: ContentItemEntity,
    connections: Connections,
    likes: List<ContentItemEntity>,
    /** 이 순간에 직접 이어 둔 좋아하는 것. 고치는 창의 칩이 이것으로 시작합니다. */
    linkedLikeSyncIds: List<String>,
    /** 순간의 날의 요약. 좋아하는 것이거나 아직 셈하는 중이면 null입니다. */
    sameDay: DayDigest?,
    /** 같은 날의 다른 행복한 순간. */
    sameDayMoments: List<ContentItemEntity>,
    onSaveMoment: suspend (text: String, date: LocalDate, imageUris: List<String>, likeSyncIds: List<String>) -> Result<Long>,
    onSaveLike: suspend (name: String, category: String, why: String, imageUris: List<String>) -> Result<Long>,
    onCreateLike: suspend (name: String) -> Result<Long>,
    onDelete: suspend () -> Result<Unit>,
    onOpenLinked: (LinkedRecord) -> Unit,
    onOpenMoment: (Long) -> Unit,
    onLink: suspend (RecordRef) -> Result<Boolean>,
    onUnlink: suspend (RecordRef) -> Result<Int>,
    /** 이을 기록 찾기. 찾을 말을 받아 이을 수 있는 기록을 돌려줍니다. */
    searchRecords: (String) -> List<LinkedRecord>,
    onOpenDay: (LocalDate) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    serverUrl: String = "",
    apiKey: String = ""
) {
    val isMoment = item.type == ContentType.HAPPY_MOMENT
    val self = remember(item.syncId) { RecordRef.of(item) }
    val date = remember(item.createdAt) { Happy.dateOf(item.createdAt) }
    val scope = rememberCoroutineScope()
    var editing by rememberSaveable { mutableStateOf(false) }
    var linking by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    if (editing) {
        if (isMoment) {
            HappyMomentEditorDialog(
                title = "행복한 순간 고치기",
                initialText = item.body,
                initialDate = date,
                initialImages = item.imageUris,
                initialLikeSyncIds = linkedLikeSyncIds,
                likes = likes,
                onSave = { text, day, images, likeIds -> onSaveMoment(text, day, images, likeIds).map { } },
                onCreateLike = onCreateLike,
                onDismiss = { editing = false },
                serverUrl = serverUrl,
                apiKey = apiKey
            )
        } else {
            LikeEditorDialog(
                title = "좋아하는 것 고치기",
                initialName = item.title,
                initialCategory = item.category,
                initialWhy = item.body,
                initialImages = item.imageUris,
                onSave = { name, category, why, images -> onSaveLike(name, category, why, images).map { } },
                onDismiss = { editing = false },
                serverUrl = serverUrl,
                apiKey = apiKey
            )
        }
    }
    if (linking) {
        LinkPickerDialog(
            self = self,
            linked = connections.direct.mapTo(HashSet()) { it.ref },
            searchRecords = searchRecords,
            onPick = { ref ->
                onLink(ref)
                    .onSuccess { added -> onMessage(if (added) "이었습니다." else "이미 이어져 있습니다.") }
                    .onFailure { onMessage(it.message ?: "잇지 못했습니다.") }
            },
            onDismiss = { linking = false }
        )
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("${item.type.label} 지우기") },
            text = { Text("'${item.title}'을(를) 지울까요? 이어진 기록들은 그대로 남습니다.") },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag(happyConfirmDeleteTag),
                    onClick = {
                        confirmingDelete = false
                        scope.launch { onDelete().onFailure { onMessage(it.message ?: "지우지 못했습니다.") } }
                    }
                ) { Text("지우기") }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("취소") } }
        )
    }
    viewerIndex?.let { index ->
        AttachmentViewerDialog(
            uris = item.imageUris,
            startIndex = index,
            onDismiss = { viewerIndex = null },
            serverUrl = serverUrl,
            apiKey = apiKey
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(happyDetailListTag),
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
                    Text(
                        text = if (isMoment) "${item.type.label} · ${formatHappyDate(date)}"
                        else listOf(item.type.label, item.category.trim()).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (isMoment) {
                        Text(item.body.ifBlank { item.title }, style = MaterialTheme.typography.titleLarge)
                    } else {
                        Text(item.title, style = MaterialTheme.typography.headlineSmall)
                        if (item.body.isNotBlank()) {
                            Text(item.body, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    if (item.imageUris.isNotEmpty()) {
                        AttachmentThumbnails(
                            imageUris = item.imageUris,
                            videoUris = emptyList(),
                            serverUrl = serverUrl,
                            apiKey = apiKey,
                            onOpenImage = { index -> viewerIndex = index }
                        )
                    }
                    if (!isMoment && connections.happyMoments.isNotEmpty()) {
                        Text(
                            text = "행복한 순간 ${connections.happyMoments.size}개에 있었습니다.",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { editing = true }, modifier = Modifier.testTag(happyEditTag)) { Text("고치기") }
                        TextButton(onClick = { confirmingDelete = true }, modifier = Modifier.testTag(happyDeleteTag)) {
                            Text("지우기", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        item(key = "connections") {
            ConnectionsSection(
                connections = connections,
                onLink = { linking = true },
                onOpen = onOpenLinked,
                onUnlink = { record ->
                    scope.launch {
                        onUnlink(record.ref)
                            .onSuccess { onMessage("잇기를 풀었습니다.") }
                            .onFailure { onMessage(it.message ?: "풀지 못했습니다.") }
                    }
                },
                onPromote = { record ->
                    scope.launch {
                        onLink(record.ref)
                            .onSuccess { onMessage("이었습니다.") }
                            .onFailure { onMessage(it.message ?: "잇지 못했습니다.") }
                    }
                }
            )
        }

        if (isMoment) {
            item(key = "same-day") {
                SameDaySection(
                    date = date,
                    digest = sameDay?.takeIf { it.score.date == date },
                    moments = sameDayMoments,
                    onOpenMoment = onOpenMoment,
                    onOpenDay = onOpenDay
                )
            }
        }
    }
}

/**
 * 이어진 기록 — 직접 이은 것과 이름이 나온 곳(사용자가 정함, 2026-11-12). 이름이 나온 곳은 저절로 보이는 것이라 지울 수
 * 없고, *잇기*로 직접 이은 것으로 올릴 수 있습니다.
 */
@Composable
private fun ConnectionsSection(
    connections: Connections,
    onLink: () -> Unit,
    onOpen: (LinkedRecord) -> Unit,
    onUnlink: (LinkedRecord) -> Unit,
    onPromote: (LinkedRecord) -> Unit
) {
    HappySection(title = "이어진 기록") {
        if (connections.isEmpty) {
            Text(
                text = "일기·책·루틴·할 일·글귀 같은 다른 기록과 이어 보세요. 이 이름이 다른 글에 나오면 저절로 여기에 보입니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (connections.direct.isNotEmpty()) {
            Text("직접 이은 것", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            connections.direct.forEach { record ->
                LinkedRow(
                    record = record,
                    onOpen = { onOpen(record) },
                    modifier = Modifier.testTag(happyLinkedRowTag(record.ref.syncId))
                ) {
                    var confirming by remember(record.ref) { mutableStateOf(false) }
                    TextButton(
                        onClick = { if (confirming) onUnlink(record) else confirming = true },
                        modifier = Modifier.testTag(happyUnlinkTag(record.ref.syncId))
                    ) {
                        Text(
                            text = if (confirming) "정말 풀기" else "풀기",
                            color = if (confirming) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        if (connections.mentions.isNotEmpty()) {
            Text("이름이 나온 곳", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            connections.mentions.take(MENTIONS_SHOWN).forEach { record ->
                LinkedRow(
                    record = record,
                    onOpen = { onOpen(record) },
                    modifier = Modifier.testTag(happyMentionRowTag(record.ref.syncId))
                ) {
                    TextButton(onClick = { onPromote(record) }, modifier = Modifier.testTag(happyPromoteTag(record.ref.syncId))) {
                        Text("잇기")
                    }
                }
            }
            if (connections.mentions.size > MENTIONS_SHOWN) {
                Text(
                    text = "이름이 나온 곳이 ${connections.mentions.size - MENTIONS_SHOWN}개 더 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        FilledTonalButton(onClick = onLink, modifier = Modifier.testTag(happyLinkTag)) { Text("기록 잇기") }
    }
}

@Composable
private fun LinkedRow(
    record: LinkedRecord,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onOpen)
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(record.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(record.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (record.snippet.isNotBlank()) {
                Text(
                    text = "\"${record.snippet}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing()
    }
}

/**
 * 같은 날 — 그 순간의 날에 남긴 다른 기록. 한 것만 늘어놓습니다(못 한 것을 세지 않습니다). 그날 남긴 것이 없으면 칸을
 * 두지 않습니다. 더 보려면 *그날 요약*으로 엽니다.
 */
@Composable
private fun SameDaySection(
    date: LocalDate,
    digest: DayDigest?,
    moments: List<ContentItemEntity>,
    onOpenMoment: (Long) -> Unit,
    onOpenDay: (LocalDate) -> Unit
) {
    val lines = remember(digest) {
        buildList {
            val score = digest?.score
            score?.mood?.let { add("기분 ${it.emoji} ${it.label}") }
            score?.walkSteps?.takeIf { it > 0 }?.let { add(Walking.formatSteps(it)) }
            digest?.walks?.takeIf { it.isNotEmpty() }?.let { walks ->
                add("걷기 ${walks.size}번 · ${Walking.formatDistance(walks.sumOf { it.distanceMeters })}")
            }
            digest?.routineTitles?.distinct()?.takeIf { it.isNotEmpty() }?.let { titles ->
                add("루틴 ${titles.size}개 · " + titles.take(3).joinToString(", ") + if (titles.size > 3) " 외" else "")
            }
            score?.todosDone?.takeIf { it > 0 }?.let { add("할 일 ${it}개 끝냄") }
            digest?.bookTitles?.distinct()?.takeIf { it.isNotEmpty() }?.let { titles ->
                add("독서 · " + titles.joinToString(", ") { "『$it』" })
            }
            score?.diaries?.takeIf { it > 0 }?.let { add("일기 ${it}편") }
            score?.quotesRead?.takeIf { it > 0 }?.let { add("글귀 ${it}개 읽음") }
        }
    }
    if (lines.isEmpty() && moments.isEmpty()) return
    HappySection(title = "같은 날 · ${formatHappyDate(date)}", modifier = Modifier.testTag(happySameDayTag)) {
        lines.forEach { line -> Text(line, style = MaterialTheme.typography.bodyMedium) }
        moments.forEach { moment ->
            Text(
                text = "✦ " + moment.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onOpenMoment(moment.id) }
                    .padding(vertical = 4.dp)
            )
        }
        TextButton(onClick = { onOpenDay(date) }, modifier = Modifier.testTag(happyDayOpenTag)) { Text("그날 요약 ›") }
    }
}

@Composable
private fun HappySection(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

/** 이을 기록 찾기. 검색과 같은 규칙으로 찾고, 고르면 바로 잇습니다. */
@Composable
private fun LinkPickerDialog(
    self: RecordRef,
    linked: Set<RecordRef>,
    searchRecords: (String) -> List<LinkedRecord>,
    onPick: suspend (RecordRef) -> Result<Boolean>,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query, searchRecords) {
        if (query.isBlank()) emptyList()
        else searchRecords(query).filter { it.ref != self }.distinctBy { it.ref }.take(LINK_RESULTS_SHOWN)
    }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("기록 잇기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("찾을 말") },
                    placeholder = { Text("일기·책·루틴·할 일·글귀의 말") },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().testTag(happyLinkSearchFieldTag)
                )
                when {
                    query.isBlank() -> Text(
                        text = "이을 기록의 제목이나 글 속의 말로 찾습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    results.isEmpty() -> Text(
                        text = "찾은 기록이 없습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(results, key = { it.ref.kind + ":" + it.ref.syncId }) { record ->
                        val already = record.ref in linked
                        LinkedRow(
                            record = record,
                            onOpen = {
                                if (already || saving) return@LinkedRow
                                saving = true
                                scope.launch {
                                    val result = onPick(record.ref)
                                    saving = false
                                    if (result.isSuccess) onDismiss()
                                }
                            },
                            modifier = Modifier.testTag(happyLinkResultTag(record.ref.syncId))
                        ) {
                            if (already) {
                                Text(
                                    text = "이어짐",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("닫기") } }
    )
}

/**
 * 행복한 순간을 적거나 고치는 창(사용자가 정함, 2026-11-12 — 글·날짜·사진 + 좋아하는 것 칩).
 *
 * **글에 이름이 나온 좋아하는 것은 미리 골라 둡니다.** 직접 뺀 것은 글에 이름이 있어도 다시 고르지 않습니다 — 사용자가 뺀
 * 것을 화면이 되돌리면 안 됩니다. *+ 새로*로 적은 좋아하는 것은 목록에 들어오는 대로 골라 둡니다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HappyMomentEditorDialog(
    title: String,
    initialText: String,
    initialDate: LocalDate,
    initialImages: List<String>,
    initialLikeSyncIds: List<String>,
    likes: List<ContentItemEntity>,
    onSave: suspend (text: String, date: LocalDate, imageUris: List<String>, likeSyncIds: List<String>) -> Result<Unit>,
    onCreateLike: suspend (name: String) -> Result<Long>,
    onDismiss: () -> Unit,
    serverUrl: String,
    apiKey: String
) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    var text by rememberSaveable { mutableStateOf(initialText) }
    // LocalDate를 그대로 담지 않고 글자로 둡니다(화면을 돌려도 남게).
    var dateText by rememberSaveable { mutableStateOf(initialDate.toString()) }
    val date = remember(dateText) { runCatching { LocalDate.parse(dateText) }.getOrDefault(today) }
    var images by rememberSaveable { mutableStateOf(initialImages) }
    var picked by rememberSaveable { mutableStateOf(initialLikeSyncIds) }
    var dropped by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var pendingLikeIds by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var newLikeName by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val named = remember(text, likes) { Happy.likesNamedIn(text, likes).map { it.syncId } }
    val chosen = remember(picked, dropped, named) { (picked + named.filter { it !in dropped }).distinct() }
    // 글에 나온 것이 앞, 그다음 이름 차례.
    val ordered = remember(likes, named) {
        likes.sortedWith(compareByDescending<ContentItemEntity> { it.syncId in named }.thenBy { it.title })
    }

    LaunchedEffect(likes, pendingLikeIds) {
        if (pendingLikeIds.isEmpty()) return@LaunchedEffect
        val arrived = likes.filter { it.id in pendingLikeIds }
        if (arrived.isEmpty()) return@LaunchedEffect
        val arrivedSyncIds = arrived.map { it.syncId }
        picked = (picked + arrivedSyncIds).distinct()
        dropped = dropped - arrivedSyncIds.toSet()
        pendingLikeIds = pendingLikeIds - arrived.map { it.id }.toSet()
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("무엇이 행복했나요") },
                    placeholder = { Text("예: 퇴근길 노을이 너무 예뻤다") },
                    minLines = 3,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().testTag(momentTextFieldTag)
                )
                Text("날짜", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val yesterday = today.minusDays(1)
                    val other = date != today && date != yesterday
                    FilterChip(
                        selected = date == today,
                        onClick = { dateText = today.toString() },
                        label = { Text("오늘") },
                        modifier = Modifier.testTag(momentDateTodayTag)
                    )
                    FilterChip(
                        selected = date == yesterday,
                        onClick = { dateText = yesterday.toString() },
                        label = { Text("어제") },
                        modifier = Modifier.testTag(momentDateYesterdayTag)
                    )
                    FilterChip(
                        selected = other,
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth -> dateText = LocalDate.of(year, month + 1, dayOfMonth).toString() },
                                date.year,
                                date.monthValue - 1,
                                date.dayOfMonth
                            ).apply {
                                // 오지 않은 날의 행복은 아직 적을 수 없습니다.
                                datePicker.maxDate = System.currentTimeMillis()
                            }.show()
                        },
                        label = { Text(if (other) formatHappyDate(date) else "날짜 고르기") },
                        modifier = Modifier.testTag(momentDatePickTag)
                    )
                }
                Text("사진", style = MaterialTheme.typography.labelLarge)
                PhotoPicker(images = images, onChange = { images = it }, enabled = !saving, serverUrl = serverUrl, apiKey = apiKey)
                Text("좋아하는 것과 잇기", style = MaterialTheme.typography.labelLarge)
                if (likes.isEmpty()) {
                    Text(
                        text = "아직 좋아하는 것이 없습니다. + 새로로 바로 적을 수 있습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ordered.forEach { like ->
                        val on = like.syncId in chosen
                        FilterChip(
                            selected = on,
                            onClick = {
                                if (on) {
                                    picked = picked - like.syncId
                                    dropped = (dropped + like.syncId).distinct()
                                } else {
                                    picked = (picked + like.syncId).distinct()
                                    dropped = dropped - like.syncId
                                }
                            },
                            label = { Text(like.title) },
                            enabled = !saving,
                            modifier = Modifier.testTag(momentLikeChipTag(like.syncId))
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = { newLikeName = newLikeName ?: "" },
                        label = { Text("+ 새로") },
                        enabled = !saving,
                        modifier = Modifier.testTag(momentNewLikeTag)
                    )
                }
                val autoPicked = ordered.filter { it.syncId in named && it.syncId !in dropped }
                if (autoPicked.isNotEmpty()) {
                    Text(
                        text = "글에 나온 " + autoPicked.joinToString(", ") { "'${it.title}'" } + "은(는) 골라 두었습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                newLikeName?.let { value ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { newLikeName = it },
                            label = { Text("새 좋아하는 것") },
                            placeholder = { Text("예: 노을") },
                            singleLine = true,
                            enabled = !saving,
                            modifier = Modifier.weight(1f).testTag(momentNewLikeFieldTag)
                        )
                        TextButton(
                            enabled = value.isNotBlank() && !saving,
                            onClick = {
                                scope.launch {
                                    onCreateLike(value.trim())
                                        .onSuccess { id ->
                                            pendingLikeIds = (pendingLikeIds + id).distinct()
                                            newLikeName = null
                                        }
                                        .onFailure { error = it.message ?: "적지 못했습니다." }
                                }
                            },
                            modifier = Modifier.testTag(momentNewLikeAddTag)
                        ) { Text("추가") }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank() && !saving,
                modifier = Modifier.testTag(momentSaveTag),
                onClick = {
                    saving = true
                    error = null
                    scope.launch {
                        val known = likes.mapTo(HashSet()) { it.syncId }
                        val result = onSave(text.trim(), date, images, chosen.filter { it in known })
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

/** 좋아하는 것을 적거나 고치는 창 — 이름·갈래(칩 + 직접)·왜 좋은지·사진(사용자가 정함, 2026-11-12). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LikeEditorDialog(
    title: String,
    initialName: String,
    initialCategory: String,
    initialWhy: String,
    initialImages: List<String>,
    onSave: suspend (name: String, category: String, why: String, imageUris: List<String>) -> Result<Unit>,
    onDismiss: () -> Unit,
    serverUrl: String,
    apiKey: String
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var category by rememberSaveable { mutableStateOf(initialCategory.trim()) }
    // 정해 둔 갈래가 아니면 "직접"으로 엽니다.
    var custom by rememberSaveable { mutableStateOf(initialCategory.isNotBlank() && initialCategory.trim() !in Happy.LIKE_CATEGORIES) }
    var why by rememberSaveable { mutableStateOf(initialWhy) }
    var images by rememberSaveable { mutableStateOf(initialImages) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("이름") },
                    placeholder = { Text("예: 노을, 떡볶이, 한강 산책") },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().testTag(likeNameFieldTag)
                )
                Text("갈래", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Happy.LIKE_CATEGORIES.forEach { choice ->
                        val on = !custom && category == choice
                        FilterChip(
                            selected = on,
                            onClick = {
                                custom = false
                                category = if (on) "" else choice
                            },
                            label = { Text(choice) },
                            enabled = !saving,
                            modifier = Modifier.testTag(likeCategoryChipTag(choice))
                        )
                    }
                    FilterChip(
                        selected = custom,
                        onClick = {
                            custom = !custom
                            if (category in Happy.LIKE_CATEGORIES || !custom) category = ""
                        },
                        label = { Text(if (custom && category.isNotBlank()) "직접 $category" else "직접") },
                        enabled = !saving,
                        modifier = Modifier.testTag(likeCustomCategoryTag)
                    )
                }
                if (custom) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("갈래 이름") },
                        placeholder = { Text("예: 계절") },
                        singleLine = true,
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth().testTag(likeCustomCategoryFieldTag)
                    )
                }
                OutlinedTextField(
                    value = why,
                    onValueChange = { why = it },
                    label = { Text("왜 좋은가요 (선택)") },
                    minLines = 2,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().testTag(likeWhyFieldTag)
                )
                Text("사진", style = MaterialTheme.typography.labelLarge)
                PhotoPicker(images = images, onChange = { images = it }, enabled = !saving, serverUrl = serverUrl, apiKey = apiKey)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !saving,
                modifier = Modifier.testTag(likeSaveTag),
                onClick = {
                    saving = true
                    error = null
                    scope.launch {
                        val result = onSave(name.trim(), category.trim(), why.trim(), images)
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

/**
 * 사진 고르기와 고른 사진. 앱을 다시 켜도 그 사진을 읽을 수 있게 권한을 붙들어 둡니다(보관함 담기와 같게). 올리는 것은
 * 동기화가 맡습니다 — 보관함 기록의 사진과 같은 길입니다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotoPicker(
    images: List<String>,
    onChange: (List<String>) -> Unit,
    enabled: Boolean,
    serverUrl: String,
    apiKey: String
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val granted = uris.mapNotNull { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            uri.toString().takeIf(String::isNotBlank)
        }
        onChange((images + granted).distinct())
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (images.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                images.forEach { uri ->
                    Box {
                        HappyThumbnail(uri = uri, serverUrl = serverUrl, apiKey = apiKey, size = 64.dp)
                        IconButton(
                            onClick = { onChange(images - uri) },
                            enabled = enabled,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "사진 빼기", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        OutlinedButton(
            onClick = { picker.launch(arrayOf("image/*")) },
            enabled = enabled,
            modifier = Modifier.testTag(happyAddPhotoTag)
        ) { Text(if (images.isEmpty()) "+ 사진" else "+ 사진 더") }
    }
}

/** 작은 사진 한 장. 서버에 올라간 사진은 접속 키를 실어 받습니다([attachmentImageRequest]). */
@Composable
private fun HappyThumbnail(uri: String, serverUrl: String, apiKey: String, size: Dp) {
    val context = LocalContext.current
    val model = remember(uri, serverUrl, apiKey) { attachmentImageRequest(context, uri, serverUrl, apiKey) }
    if (model == null) {
        // 서버 주소를 안 넣은 기기에서 다른 기기가 붙인 사진을 받으면 여기로 옵니다.
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text("사진", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        AsyncImage(
            model = model,
            imageLoader = attachmentImageLoader(),
            contentDescription = "붙여 둔 사진",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}

internal fun formatHappyDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN))

/** 이름이 나온 곳을 다 펼쳐 둘 개수. 짧은 이름(루틴 "독서")은 일기마다 걸릴 수 있습니다. */
private const val MENTIONS_SHOWN = 8

/** 잇기 창에 늘어놓을 찾은 기록 수. */
private const val LINK_RESULTS_SHOWN = 40
