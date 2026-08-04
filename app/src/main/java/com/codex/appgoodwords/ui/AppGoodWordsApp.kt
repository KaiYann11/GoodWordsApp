package com.codex.appgoodwords.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codex.appgoodwords.data.ContentDraft
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ContentType
import com.codex.appgoodwords.data.ExposureEventEntity
import com.codex.appgoodwords.data.ExposureEventType
import com.codex.appgoodwords.data.ExposureTrigger
import com.codex.appgoodwords.ui.screen.AddContentScreen
import com.codex.appgoodwords.ui.screen.DetailScreen
import com.codex.appgoodwords.ui.screen.HistoryScreen
import com.codex.appgoodwords.ui.screen.HomeScreen
import com.codex.appgoodwords.ui.screen.LibraryScreen
import com.codex.appgoodwords.ui.screen.RoutineScreen
import com.codex.appgoodwords.ui.screen.SettingsScreen
import com.codex.appgoodwords.ui.theme.AppGoodWordsTheme
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class AppTab(
    val title: String
) {
    HOME("홈"),
    LIBRARY("보관함"),
    ROUTINES("루틴"),
    ADD("추가"),
    HISTORY("이력"),
    SETTINGS("설정")
}

private data class AppDestination(
    val tab: AppTab,
    val selectedItemId: Long? = null,
    val editingItemId: Long? = null,
    val returnTabAfterEdit: AppTab = AppTab.HOME
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppGoodWordsApp(
    viewModel: MainViewModel
) {
    val allItems by viewModel.allItems.collectAsStateWithLifecycle()
    val historyEvents by viewModel.historyEvents.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val serverSyncSettings by viewModel.serverSyncSettings.collectAsStateWithLifecycle()
    val sharedText by viewModel.sharedText.collectAsStateWithLifecycle()
    val openItemRequest by viewModel.openItemRequest.collectAsStateWithLifecycle()
    val confirmedTodayIds by viewModel.confirmedTodayIds.collectAsStateWithLifecycle()
    val routines by viewModel.routines.collectAsStateWithLifecycle()
    val routineChecks by viewModel.routineChecks.collectAsStateWithLifecycle()
    val routineTodayCounts by viewModel.routineTodayCounts.collectAsStateWithLifecycle()
    val routineMemos by viewModel.routineMemos.collectAsStateWithLifecycle()
    val syncBackups by viewModel.syncBackups.collectAsStateWithLifecycle()
    val syncBackupDirectory by viewModel.syncBackupDirectory.collectAsStateWithLifecycle()

    val existingTags = remember(allItems) {
        allItems
            .flatMap { it.tags }
            .map(String::trim)
            .filter(String::isNotBlank)
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenBy { it.key }
            )
            .map { it.key }
    }

    var navStack by rememberSaveable { mutableStateOf(listOf(tabRoute(AppTab.HOME))) }
    var addFormVersion by rememberSaveable { mutableIntStateOf(0) }
    var confirmFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var confirmFeedbackToken by remember { mutableLongStateOf(0L) }

    val destination = remember(navStack) { parseRoute(navStack.last()) }
    val currentTab = destination.tab
    val selectedItem = allItems.firstOrNull { it.id == destination.selectedItemId }
    val editingItem = allItems.firstOrNull { it.id == destination.editingItemId }
    val canNavigateBack = navStack.size > 1

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val saveableStateHolder = rememberSaveableStateHolder()

    fun pushRoute(route: String) {
        if (navStack.lastOrNull() != route) {
            navStack = navStack + route
        }
    }

    fun popRoute() {
        if (navStack.size > 1) {
            navStack = navStack.dropLast(1)
        }
    }

    fun popToRoute(route: String) {
        val index = navStack.indexOfLast { it == route }
        navStack = if (index >= 0) {
            navStack.take(index + 1)
        } else {
            listOf(tabRoute(AppTab.HOME), route)
        }
    }

    fun showConfirmFeedback(result: Result<Boolean>) {
        confirmFeedbackMessage = if (result.isSuccess) {
            if (result.getOrDefault(false)) {
                "오늘 확인으로 체크했습니다."
            } else {
                "오늘 확인을 취소했습니다."
            }
        } else {
            result.exceptionOrNull()?.message ?: "확인 상태를 바꾸지 못했습니다."
        }
        confirmFeedbackToken = SystemClock.elapsedRealtimeNanos()
    }

    fun toggleConfirmed(item: ContentItemEntity, showMessage: Boolean) {
        coroutineScope.launch {
            val result = viewModel.toggleContentConfirmed(item.id)
            if (showMessage) {
                showConfirmFeedback(result)
            }
        }
    }

    fun openItemDetail(
        tab: AppTab,
        itemId: Long,
        trigger: ExposureTrigger = ExposureTrigger.DETAIL_OPEN
    ) {
        viewModel.recordContentViewed(itemId, trigger)
        pushRoute(detailRoute(tab, itemId))
    }

    fun openFreshAddForm() {
        viewModel.clearSharedText()
        addFormVersion += 1
        if (navStack.lastOrNull() == addRoute()) {
            return
        }
        pushRoute(addRoute())
    }

    BackHandler(enabled = canNavigateBack) {
        popRoute()
    }

    LaunchedEffect(confirmFeedbackToken) {
        val token = confirmFeedbackToken
        if (token == 0L) return@LaunchedEffect
        delay(900)
        if (confirmFeedbackToken == token) {
            confirmFeedbackMessage = null
        }
    }

    LaunchedEffect(sharedText, destination.editingItemId) {
        if (!sharedText.isNullOrBlank() && destination.editingItemId == null && navStack.last() != addRoute()) {
            pushRoute(addRoute())
        }
    }

    LaunchedEffect(openItemRequest, allItems, currentTab) {
        val targetId = openItemRequest ?: return@LaunchedEffect
        if (allItems.any { it.id == targetId }) {
            pushRoute(detailRoute(currentTab, targetId))
            viewModel.consumeOpenItemRequest()
        }
    }

    AppGoodWordsTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    title = {
                        Text(
                            text = when {
                                selectedItem != null -> selectedItem.title.ifBlank { "상세 보기" }
                                destination.editingItemId != null -> "항목 수정"
                                else -> currentTab.title
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        if (canNavigateBack) {
                            IconButton(onClick = ::popRoute) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "뒤로 가기"
                                )
                            }
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (selectedItem == null) {
                    NavigationBar {
                        listOf(AppTab.HOME, AppTab.LIBRARY, AppTab.ROUTINES, AppTab.ADD, AppTab.HISTORY, AppTab.SETTINGS)
                            .forEach { tab ->
                                val selected = currentTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        if (tab == AppTab.ADD) {
                                            openFreshAddForm()
                                        } else {
                                            pushRoute(tabRoute(tab))
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = when (tab) {
                                                AppTab.HOME -> Icons.Outlined.Home
                                                AppTab.LIBRARY -> Icons.AutoMirrored.Outlined.LibraryBooks
                                                AppTab.ROUTINES -> Icons.Outlined.CheckCircle
                                                AppTab.ADD -> Icons.Outlined.Add
                                                AppTab.HISTORY -> Icons.Outlined.History
                                                AppTab.SETTINGS -> Icons.Outlined.Settings
                                            },
                                            contentDescription = tab.title
                                        )
                                    },
                                    label = { Text(tab.title) }
                                )
                            }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFF7FBFF),
                                Color(0xFFEFF4FF),
                                Color(0xFFFDF9FF)
                            )
                        )
                    )
            ) {
                saveableStateHolder.SaveableStateProvider(navStack.last()) {
                if (selectedItem != null) {
                    DetailScreen(
                        modifier = Modifier.padding(innerPadding),
                        item = selectedItem,
                        confirmedToday = selectedItem.id in confirmedTodayIds,
                        onEdit = { item ->
                            pushRoute(editRoute(currentTab, item.id))
                            addFormVersion += 1
                        },
                        onDelete = { item ->
                            coroutineScope.launch {
                                val result = viewModel.deleteContent(item.id)
                                val message = if (result.isSuccess) {
                                    popRoute()
                                    "항목을 삭제했습니다."
                                } else {
                                    result.exceptionOrNull()?.message ?: "항목을 삭제하지 못했습니다."
                                }
                                snackbarHostState.showSnackbar(message)
                            }
                        },
                        onConfirm = { item ->
                            toggleConfirmed(item, showMessage = true)
                        },
                        onToggleFavorite = { item ->
                            viewModel.toggleFavorite(item.id, !item.isFavorite)
                        }
                    )
                } else {
                    when (currentTab) {
                        AppTab.HOME -> HomeScreen(
                            modifier = Modifier.padding(innerPadding),
                            todayItems = allItems,
                            settings = settings,
                            confirmedTodayIds = confirmedTodayIds,
                            onToggleFavorite = { item ->
                                viewModel.toggleFavorite(item.id, !item.isFavorite)
                            },
                            onConfirmItem = { item ->
                                toggleConfirmed(item, showMessage = false)
                            },
                            onOpenItem = { item ->
                                openItemDetail(AppTab.HOME, item.id)
                            }
                        )

                        AppTab.LIBRARY -> LibraryScreen(
                            modifier = Modifier.padding(innerPadding),
                            items = allItems,
                            categories = categories,
                            confirmedTodayIds = confirmedTodayIds,
                            onToggleFavorite = { item ->
                                viewModel.toggleFavorite(item.id, !item.isFavorite)
                            },
                            onConfirmItem = { item ->
                                toggleConfirmed(item, showMessage = true)
                            },
                            onOpenItem = { item ->
                                openItemDetail(AppTab.LIBRARY, item.id)
                            },
                            onResetTodayConfirmed = {
                                coroutineScope.launch {
                                    val result = viewModel.resetTodayConfirmed()
                                    val message = if (result.isSuccess) {
                                        val removedCount = result.getOrDefault(0)
                                        if (removedCount > 0) {
                                            "${removedCount}개 항목을 오늘 안읽음으로 되돌렸습니다."
                                        } else {
                                            "오늘 읽음으로 표시된 항목이 없습니다."
                                        }
                                    } else {
                                        result.exceptionOrNull()?.message ?: "오늘 읽음 초기화에 실패했습니다."
                                    }
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        )

                        AppTab.ROUTINES -> RoutineScreen(
                            modifier = Modifier.padding(innerPadding),
                            routines = routines,
                            todayCounts = routineTodayCounts,
                            checks = routineChecks,
                            memos = routineMemos,
                            onSaveRoutine = { draft ->
                                coroutineScope.launch {
                                    val result = viewModel.saveRoutine(draft)
                                    val message = if (result.isSuccess) {
                                        "루틴을 저장했습니다."
                                    } else {
                                        result.exceptionOrNull()?.message ?: "루틴을 저장하지 못했습니다."
                                    }
                                    snackbarHostState.showSnackbar(message)
                                }
                            },
                            onDeleteRoutine = { routine ->
                                coroutineScope.launch {
                                    val result = viewModel.deleteRoutine(routine.id)
                                    val message = if (result.isSuccess) {
                                        "루틴을 삭제했습니다."
                                    } else {
                                        result.exceptionOrNull()?.message ?: "루틴을 삭제하지 못했습니다."
                                    }
                                    snackbarHostState.showSnackbar(message)
                                }
                            },
                            onSaveMemo = { routine, body ->
                                coroutineScope.launch {
                                    val result = viewModel.saveRoutineMemo(routine.id, body)
                                    val message = if (result.isSuccess) {
                                        "메모를 저장했습니다."
                                    } else {
                                        result.exceptionOrNull()?.message ?: "메모를 저장하지 못했습니다."
                                    }
                                    snackbarHostState.showSnackbar(message)
                                }
                            },
                            onDeleteMemo = { memo ->
                                coroutineScope.launch {
                                    val result = viewModel.deleteRoutineMemo(memo.id)
                                    val message = if (result.isSuccess) {
                                        "메모를 삭제했습니다."
                                    } else {
                                        result.exceptionOrNull()?.message ?: "메모를 삭제하지 못했습니다."
                                    }
                                    snackbarHostState.showSnackbar(message)
                                }
                            },
                            onCheckRoutine = { routine ->
                                coroutineScope.launch {
                                    val result = viewModel.checkRoutine(routine.id)
                                    val message = if (result.isSuccess) {
                                        "${routine.title}: 오늘 ${result.getOrDefault(0)}회"
                                    } else {
                                        result.exceptionOrNull()?.message ?: "루틴을 체크하지 못했습니다."
                                    }
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        )

                        AppTab.HISTORY -> HistoryScreen(
                            modifier = Modifier.padding(innerPadding),
                            events = historyEvents,
                            onOpenItem = { itemId ->
                                openItemDetail(AppTab.HISTORY, itemId)
                            },
                            onDeleteEvents = { eventIds, onDeleted ->
                                coroutineScope.launch {
                                    val result = viewModel.deleteHistoryEvents(eventIds)
                                    val message = if (result.isSuccess) {
                                        onDeleted()
                                        "${result.getOrDefault(0)}개의 이력을 삭제했습니다."
                                    } else {
                                        result.exceptionOrNull()?.message ?: "이력을 삭제하지 못했습니다."
                                    }
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        )

                        AppTab.ADD -> AddContentScreen(
                            modifier = Modifier.padding(innerPadding),
                            categories = categories,
                            existingTags = existingTags,
                            sharedText = sharedText,
                            initialDraft = editingItem?.let(ContentDraft::fromItem),
                            formVersion = addFormVersion,
                            submitLabel = if (editingItem != null) "수정 완료" else "저장하기",
                            secondaryActionLabel = if (editingItem != null) "수정 취소" else null,
                            onSecondaryAction = if (editingItem != null) {
                                { popRoute() }
                            } else {
                                null
                            },
                            onSharedTextConsumed = {
                                if (destination.editingItemId == null) {
                                    viewModel.clearSharedText()
                                }
                            },
                            onSave = { draft ->
                                coroutineScope.launch {
                                    val editingItemId = destination.editingItemId
                                    val wasEditing = editingItemId != null
                                    val result = viewModel.saveContent(
                                        if (editingItemId != null) {
                                            draft.copy(id = editingItemId)
                                        } else {
                                            draft
                                        }
                                    )
                                    val message = if (result.isSuccess) {
                                        viewModel.clearSharedText()
                                        addFormVersion += 1
                                        if (wasEditing) {
                                            popToRoute(tabRoute(destination.returnTabAfterEdit))
                                            "수정했습니다."
                                        } else {
                                            if (navStack.size > 1 && navStack.last() == addRoute()) {
                                                popRoute()
                                            } else {
                                                navStack = listOf(tabRoute(AppTab.HOME))
                                            }
                                            "저장했습니다."
                                        }
                                    } else {
                                        result.exceptionOrNull()?.message ?: "저장하지 못했습니다."
                                    }
                                    snackbarHostState.showSnackbar(message)
                                }
                            },
                            onFetchMetadata = { url, onMetadata ->
                                coroutineScope.launch {
                                    val result = viewModel.fetchLinkMetadata(url)
                                    result.onSuccess(onMetadata)
                                    result.exceptionOrNull()?.message?.let { message ->
                                        snackbarHostState.showSnackbar(message)
                                    }
                                }
                            }
                        )

                        AppTab.SETTINGS -> SettingsScreen(
                            modifier = Modifier.padding(innerPadding),
                            settings = settings,
                            serverSyncSettings = serverSyncSettings,
                            categories = categories,
                            syncBackups = syncBackups,
                            syncBackupDirectory = syncBackupDirectory,
                            onSettingsChanged = viewModel::updateSettings,
                            onServerSyncSettingsChanged = viewModel::updateServerSyncSettings,
                            onSendTestNotification = {
                                viewModel.sendTestNotification()
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("테스트 알림을 보냈습니다.")
                                }
                            },
                            onResetViewCounts = {
                                coroutineScope.launch {
                                    val result = viewModel.resetViewCounts()
                                    val message = if (result.isSuccess) {
                                        val changedCount = result.getOrDefault(0)
                                        if (changedCount > 0) {
                                            "${changedCount}개 항목의 읽음 처리 수를 초기화했습니다."
                                        } else {
                                            "초기화할 읽음 처리 수가 없습니다."
                                        }
                                    } else {
                                        result.exceptionOrNull()?.message ?: "읽음 처리 수를 초기화하지 못했습니다."
                                    }
                                    snackbarHostState.showSnackbar(message)
                                }
                            },
                            onExportRequested = { uri ->
                                coroutineScope.launch {
                                    val result = viewModel.exportData(uri)
                                    val message = if (result.isSuccess) {
                                        "${result.getOrDefault(0)}개 항목을 JSON으로 내보냈습니다."
                                    } else {
                                        result.exceptionOrNull()?.message ?: "데이터를 내보내지 못했습니다."
                                    }
                                    snackbarHostState.showSnackbar(message)
                                }
                            },
                            onImportRequested = { uri ->
                                coroutineScope.launch {
                                    val result = viewModel.importData(uri)
                                    val message = if (result.isSuccess) {
                                        val imported = result.getOrThrow()
                                        "${imported.itemCount}개 항목, ${imported.eventCount}개 이력, ${imported.routineCount}개 루틴을 복원했습니다."
                                    } else {
                                        result.exceptionOrNull()?.message ?: "데이터를 가져오지 못했습니다."
                                    }
                                    snackbarHostState.showSnackbar(message)
                                }
                            },
                            onTestServerConnection = {
                                coroutineScope.launch {
                                    val result = viewModel.testServerConnection()
                                    val message = if (result.isSuccess) {
                                        val info = result.getOrThrow()
                                        val summary = "서버 연결 성공: 항목 ${info.itemCount}개, 루틴 ${info.routineCount}개, 이력 ${info.eventCount}개"
                                        if (info.schemaMatches) {
                                            summary
                                        } else {
                                            "$summary (스키마 다름: 서버 ${info.serverSchemaVersion}, 앱 ${info.appSchemaVersion})"
                                        }
                                    } else {
                                        result.exceptionOrNull()?.message ?: "서버에 연결하지 못했습니다."
                                    }
                                    snackbarHostState.showSnackbar(message)
                                }
                            },
                            onUploadToServer = {
                                coroutineScope.launch {
                                    val result = viewModel.uploadDataToServer()
                                    val message = if (result.isSuccess) {
                                        val uploaded = result.getOrThrow()
                                        "서버에 ${uploaded.counts.itemCount}개 항목, ${uploaded.counts.routineCount}개 루틴을 업로드했습니다. 이전 서버 데이터는 백업에 저장했습니다."
                                    } else {
                                        result.exceptionOrNull()?.message ?: "서버 업로드에 실패했습니다."
                                    }
                                    snackbarHostState.showSnackbar(message)
                                }
                            },
                            onDownloadFromServer = {
                                coroutineScope.launch {
                                    val result = viewModel.downloadDataFromServer()
                                    val message = if (result.isSuccess) {
                                        val imported = result.getOrThrow().counts
                                        "서버에서 ${imported.itemCount}개 항목, ${imported.eventCount}개 이력, ${imported.routineCount}개 루틴을 가져왔습니다. 이전 기기 데이터는 백업에 저장했습니다."
                                    } else {
                                        result.exceptionOrNull()?.message ?: "서버에서 가져오지 못했습니다."
                                    }
                                    snackbarHostState.showSnackbar(message)
                                }
                            },
                            onRestoreBackup = { backup ->
                                coroutineScope.launch {
                                    val result = viewModel.restoreSyncBackup(backup)
                                    val message = if (result.isSuccess) {
                                        val restored = result.getOrThrow()
                                        "백업에서 ${restored.itemCount}개 항목, ${restored.eventCount}개 이력, ${restored.routineCount}개 루틴을 복원했습니다."
                                    } else {
                                        result.exceptionOrNull()?.message ?: "백업을 복원하지 못했습니다."
                                    }
                                    snackbarHostState.showSnackbar(message)
                                }
                            },
                            onDeleteCategory = { category ->
                                coroutineScope.launch {
                                    val result = viewModel.deleteCategory(category)
                                    val message = if (result.isSuccess) {
                                        val changedCount = result.getOrDefault(0)
                                        "'$category' 카테고리를 삭제하고 ${changedCount}개 항목을 미분류로 바꿨습니다."
                                    } else {
                                        result.exceptionOrNull()?.message ?: "카테고리를 삭제하지 못했습니다."
                                    }
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        )
                    }
                    }
                }

                ConfirmFeedbackBanner(
                    message = confirmFeedbackMessage,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = innerPadding.calculateTopPadding() + 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ConfirmFeedbackBanner(
    message: String?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 }),
        modifier = modifier
    ) {
        if (message != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                )
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun buildTodayItems(
    allItems: List<ContentItemEntity>,
    historyEvents: List<ExposureEventEntity>
): List<ContentItemEntity> {
    if (allItems.isEmpty() || historyEvents.isEmpty()) {
        return emptyList()
    }

    val zoneId = ZoneId.systemDefault()
    val todayStart = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()
    val itemMap = allItems.associateBy { it.id }

    return historyEvents
        .asSequence()
        .filter { event ->
            event.eventType == ExposureEventType.SURFACED &&
                event.occurredAt >= todayStart
        }
        .sortedByDescending { it.occurredAt }
        .mapNotNull { event -> itemMap[event.contentItemId] }
        .distinctBy { it.id }
        .toList()
}

private fun tabRoute(tab: AppTab): String = "tab:${tab.name}"

private fun addRoute(): String = "add"

private fun detailRoute(tab: AppTab, itemId: Long): String = "detail:${tab.name}:$itemId"

private fun editRoute(returnTab: AppTab, itemId: Long): String = "edit:${returnTab.name}:$itemId"

private fun parseRoute(route: String): AppDestination {
    val parts = route.split(":")
    return when (parts.firstOrNull()) {
        "tab" -> AppDestination(tab = AppTab.valueOf(parts[1]))
        "add" -> AppDestination(tab = AppTab.ADD)
        "detail" -> AppDestination(
            tab = AppTab.valueOf(parts[1]),
            selectedItemId = parts.getOrNull(2)?.toLongOrNull()
        )
        "edit" -> AppDestination(
            tab = AppTab.ADD,
            editingItemId = parts.getOrNull(2)?.toLongOrNull(),
            returnTabAfterEdit = AppTab.valueOf(parts[1])
        )
        else -> AppDestination(tab = AppTab.HOME)
    }
}
