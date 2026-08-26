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
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codex.appgoodwords.data.ContentDraft
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.DayMood
import com.codex.appgoodwords.data.DailyStep
import com.codex.appgoodwords.data.ContentType
import com.codex.appgoodwords.data.ExposureEventEntity
import com.codex.appgoodwords.data.ExposureEventType
import com.codex.appgoodwords.data.ExposureTrigger
import com.codex.appgoodwords.data.GrowthReportEntity
import com.codex.appgoodwords.data.ReportPeriod
import com.codex.appgoodwords.data.SearchKind
import com.codex.appgoodwords.ui.screen.AddContentScreen
import com.codex.appgoodwords.ui.screen.AppLock
import com.codex.appgoodwords.ui.screen.AppLockScreen
import com.codex.appgoodwords.ui.screen.AppLockState
import com.codex.appgoodwords.ui.screen.AttachmentGalleryScreen
import com.codex.appgoodwords.ui.screen.BookScreen
import com.codex.appgoodwords.ui.screen.DetailScreen
import com.codex.appgoodwords.ui.screen.DiaryScreen
import com.codex.appgoodwords.ui.screen.GrowthFeedbackScreen
import com.codex.appgoodwords.ui.screen.HistoryScreen
import com.codex.appgoodwords.ui.screen.HomeScreen
import com.codex.appgoodwords.ui.screen.LibraryScreen
import com.codex.appgoodwords.ui.screen.LibraryTabsScreen
import com.codex.appgoodwords.ui.screen.RoutineScreen
import com.codex.appgoodwords.ui.screen.SearchScreen
import com.codex.appgoodwords.ui.screen.SettingsScreen
import com.codex.appgoodwords.ui.screen.TodoScreen
import com.codex.appgoodwords.ui.theme.AppGoodWordsTheme
import com.codex.appgoodwords.work.AppNotifications
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class AppTab(
    val title: String
) {
    HOME("홈"),
    LIBRARY("보관함"),
    ROUTINE("루틴"),
    /** 할 일은 루틴과 성격이 달라 자리를 나눴습니다. 루틴은 매일 세는 것이고 할 일은 한 번 끝나는 것입니다. */
    TODO("할 일"),
    DIARY("일기"),
    ADD("추가"),
    /** 이력은 매일 볼 화면이 아니라 설정 안으로 옮겼습니다. 하단 바에는 없습니다. */
    HISTORY("이력"),
    /** AI 돌아보기는 홈 카드에서 엽니다. 하단 바에 자리가 없습니다. */
    GROWTH("AI 돌아보기"),
    /** 붙여 둔 첨부 모아 보기. 일기 화면에서 엽니다. */
    GALLERY("모아 보기"),
    /** 검색은 어느 탭에서나 위쪽 돋보기로 엽니다. 다섯 기능을 한 번에 찾아 하단 바에 두지 않았습니다. */
    SEARCH("검색"),
    SETTINGS("설정")
}

private data class AppDestination(
    val tab: AppTab,
    val selectedItemId: Long? = null,
    val editingItemId: Long? = null,
    val returnTabAfterEdit: AppTab = AppTab.HOME,
    /**
     * 검색에서 고른 기록. 그 탭으로 가서 해당 항목까지 데려다줍니다.
     *
     * 탭만 바꿔 놓으면 찾아 놓고도 사용자가 다시 손으로 뒤져야 합니다.
     */
    val focusKind: SearchKind? = null,
    val focusId: Long? = null
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
    val dailyLoop by viewModel.dailyLoop.collectAsStateWithLifecycle()
    val dailySteps by viewModel.dailySteps.collectAsStateWithLifecycle()
    val confirmedTodayIds by viewModel.confirmedTodayIds.collectAsStateWithLifecycle()
    val routines by viewModel.routines.collectAsStateWithLifecycle()
    val routineChecks by viewModel.routineChecks.collectAsStateWithLifecycle()
    val routineTodayCounts by viewModel.routineTodayCounts.collectAsStateWithLifecycle()
    val routineMemos by viewModel.routineMemos.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val feedbackNotes by viewModel.feedbackNotes.collectAsStateWithLifecycle()
    val onThisDay by viewModel.onThisDay.collectAsStateWithLifecycle()
    val moodPractice by viewModel.moodPractice.collectAsStateWithLifecycle()
    val attachmentShots by viewModel.attachmentShots.collectAsStateWithLifecycle()
    val growthReports by viewModel.growthReports.collectAsStateWithLifecycle()
    val aiFeedbackSettings by viewModel.aiFeedbackSettings.collectAsStateWithLifecycle()
    val pendingGrowthPrompt by viewModel.pendingGrowthPrompt.collectAsStateWithLifecycle()
    val moodLogs by viewModel.moodLogs.collectAsStateWithLifecycle()
    val syncBackups by viewModel.syncBackups.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val syncBackupDirectory by viewModel.syncBackupDirectory.collectAsStateWithLifecycle()
    val diaries by viewModel.diaries.collectAsStateWithLifecycle()

    // 그날의 기분은 DayMood가 정합니다. 찍어 둔 것이 먼저고, 없으면 일기에서 봅니다.
    // 여기서 따로 셈하면 화면과 통계가 다른 기분을 말하게 됩니다.
    val today = LocalDate.now()
    val todayMoodLog = remember(moodLogs, today) { DayMood.logOn(moodLogs, today) }
    val todayMood = remember(moodLogs, diaries, today) {
        DayMood.byDate(logs = moodLogs, diaries = diaries)[today]
    }
    val todos by viewModel.todos.collectAsStateWithLifecycle()
    val books by viewModel.books.collectAsStateWithLifecycle()
    val shuffleSeed by viewModel.shuffleSeed.collectAsStateWithLifecycle()
    val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val diaryLockEnabled by viewModel.diaryLockEnabled.collectAsStateWithLifecycle()

    // 지문을 물으려면 FragmentActivity가 필요합니다. 아니면 못 묻고, 그때는 그냥 엽니다.
    val activity = LocalContext.current as? androidx.fragment.app.FragmentActivity
    val lockState by viewModel.lockState.collectAsStateWithLifecycle()

    // 사용자가 휴대폰 설정에서 권한을 바꾸고 돌아올 수 있으므로 화면이 살아날 때마다 다시 본다.
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationsBlocked by remember { mutableStateOf(!AppNotifications.canPostNotifications(context)) }
    // 정확한 알람 권한도 시스템 설정에서 켜고 돌아오는 값이라 함께 다시 봅니다.
    var canScheduleExactAlarms by remember { mutableStateOf(viewModel.canScheduleExactAlarms()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsBlocked = !AppNotifications.canPostNotifications(context)
                canScheduleExactAlarms = viewModel.canScheduleExactAlarms()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

    // AI 요청은 오래 걸리고 값이 듭니다. 도는 중인지와 왜 실패했는지를 화면이 들고 있어야
    // 사용자가 같은 것을 두 번 부르지 않습니다.
    var growthRunning by remember { mutableStateOf(false) }
    var growthError by remember { mutableStateOf("") }
    var growthPreview by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val saveableStateHolder = rememberSaveableStateHolder()

    fun pushRoute(route: String) {
        if (navStack.lastOrNull() != route) {
            navStack = navStack + route
        }
    }

    /**
     * 하단 바의 탭으로 갑니다. **쌓지 않고 뿌리를 갈아 끼웁니다.**
     *
     * 탭을 누를 때마다 쌓으면 두 가지가 어긋납니다. 탭 화면인데 위쪽에 뒤로 가기 화살표가
     * 생기고, 뒤로가기가 "들른 차례"를 되짚어서 보관함 → 일기 → 보관함으로 돌아다닌 만큼
     * 그대로 되감깁니다. 탭은 오가는 자리이지 파고드는 자리가 아닙니다.
     *
     * 파고드는 것(상세·편집·검색·설정처럼 다시 나와야 하는 것)만 [pushRoute]로 쌓습니다.
     */
    fun selectTab(tab: AppTab) {
        navStack = listOf(tabRoute(tab))
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

    /**
     * 뒤로가기.
     *
     * 파고든 것이 있으면 한 겹 나옵니다. 탭 뿌리에 있으면 홈으로 가고, 홈에서는 시스템에
     * 넘겨 앱을 닫습니다. 들른 차례를 되짚지 않는 것이 요점입니다. 어디를 거쳐 왔든
     * 일기에서 뒤로 가면 늘 홈입니다.
     *
     * 탭 뿌리에서 바로 앱을 닫지 않는 이유는, 홈이 이 앱의 시작 자리이기 때문입니다.
     * 일기를 보다 무심코 뒤로 갔을 때 앱이 꺼지면 보던 자리를 잃습니다.
     */
    BackHandler(enabled = canNavigateBack || currentTab != AppTab.HOME) {
        if (canNavigateBack) popRoute() else selectTab(AppTab.HOME)
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
        // 잠겨 있으면 아래를 아예 그리지 않습니다. 화면 위에 덮기만 하면 최근 앱 목록의
        // 미리보기에 내용이 그대로 남습니다.
        when (lockState) {
            AppLockState.CHECKING -> {
                // 설정을 읽는 동안입니다. 잠깐 비워 둡니다.
                Box(modifier = Modifier.fillMaxSize())
                return@AppGoodWordsTheme
            }

            AppLockState.LOCKED -> {
                AppLockScreen(onUnlocked = { viewModel.unlock() })
                return@AppGoodWordsTheme
            }

            AppLockState.OPEN -> Unit
        }

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
                    },
                    actions = {
                        // 검색은 특정 탭에 매이지 않습니다. 다섯 기능을 한 번에 찾기 때문입니다.
                        if (selectedItem == null && destination.editingItemId == null && currentTab != AppTab.SEARCH) {
                            IconButton(onClick = { pushRoute(tabRoute(AppTab.SEARCH)) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = "검색"
                                )
                            }
                        }
                        // 설정은 하단 바에서 빠졌으므로 어느 화면에서나 여기로 들어갑니다.
                        if (selectedItem == null && destination.editingItemId == null && currentTab != AppTab.SETTINGS) {
                            IconButton(onClick = { pushRoute(tabRoute(AppTab.SETTINGS)) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = "설정"
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
                        // 담기(+)는 하단 바에 두지 않습니다. 하단 바는 "어디로 갈지"만 담아야
                        // 읽기 쉬운데, +만 혼자 "무엇을 할지"여서 성격이 달랐습니다.
                        // 지금은 보관함의 글귀 화면 안에 있습니다.
                        // 설정은 매일 쓰는 것이 아니라 위쪽 톱니로 옮겼습니다. 하단 바는 매일
                        // 들르는 다섯 곳만 남깁니다.
                        listOf(AppTab.HOME, AppTab.LIBRARY, AppTab.ROUTINE, AppTab.TODO, AppTab.DIARY)
                            .forEach { tab ->
                                val selected = currentTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { selectTab(tab) },
                                    icon = {
                                        Icon(
                                            imageVector = when (tab) {
                                                AppTab.HOME -> Icons.Outlined.Home
                                                AppTab.LIBRARY -> Icons.AutoMirrored.Outlined.LibraryBooks
                                                AppTab.ROUTINE -> Icons.Outlined.CheckCircle
                                                AppTab.TODO -> Icons.AutoMirrored.Outlined.ListAlt
                                                AppTab.DIARY -> Icons.Outlined.EditNote
                                                AppTab.ADD -> Icons.Outlined.Add
                                                AppTab.HISTORY -> Icons.Outlined.History
                                                AppTab.GROWTH -> Icons.Outlined.AutoAwesome
                                                AppTab.GALLERY -> Icons.Outlined.Image
                                                AppTab.SEARCH -> Icons.Outlined.Search
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
                        onMakeRoutine = { title ->
                            coroutineScope.launch {
                                val result = viewModel.makeRoutineFromQuote(title)
                                snackbarHostState.showSnackbar(
                                    if (result.isSuccess) "루틴에 추가했습니다."
                                    else result.exceptionOrNull()?.message ?: "루틴을 만들지 못했습니다."
                                )
                            }
                        },
                        onMakeTodo = { title ->
                            coroutineScope.launch {
                                val result = viewModel.makeTodoFromQuote(title)
                                snackbarHostState.showSnackbar(
                                    if (result.isSuccess) "오늘 할 일에 담았습니다."
                                    else result.exceptionOrNull()?.message ?: "할 일을 만들지 못했습니다."
                                )
                            }
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
                            summary = stats,
                            notes = feedbackNotes,
                            routines = routines,
                            checks = routineChecks,
                            dailyLoop = dailyLoop,
                            // 글귀 읽기는 이제 보관함에 있습니다. 걸음마다 다른 화면으로 데려갑니다.
                            onOpenStep = { step ->
                                when (step) {
                                    DailyStep.QUOTE -> selectTab(AppTab.LIBRARY)
                                    DailyStep.ROUTINE -> selectTab(AppTab.ROUTINE)
                                    DailyStep.TODO -> selectTab(AppTab.TODO)
                                    DailyStep.DIARY -> selectTab(AppTab.DIARY)
                                }
                            },
                            latestReport = growthReports.firstOrNull(),
                            onOpenGrowth = { pushRoute(tabRoute(AppTab.GROWTH)) },
                            memories = onThisDay,
                            moodPractice = moodPractice,
                            todayMood = todayMood,
                            onPickMood = { mood -> viewModel.saveTodayMood(mood) },
                            // 일기에서 온 기분은 일기에서 고칩니다. 여기서 지울 수 있는 것은 찍어 둔 것뿐입니다.
                            onClearMood = todayMoodLog?.let { { viewModel.clearTodayMood() } },
                            // 그때 그 글을 열어 줍니다. 알려만 주면 사용자가 다시 뒤져야 합니다.
                            onOpenMemory = { memory ->
                                when (memory.kind) {
                                    SearchKind.QUOTE -> openItemDetail(AppTab.HOME, memory.id)
                                    else -> pushRoute(focusRoute(memory.kind, memory.id))
                                }
                            }
                        )

                        AppTab.LIBRARY -> LibraryTabsScreen(
                            modifier = Modifier.padding(innerPadding),
                            // 검색에서 책을 골라 왔으면 독서 쪽을 열어 줍니다.
                            requestedTab = if (destination.focusKind == SearchKind.BOOK) 1 else null,
                            requestKey = destination.focusId,
                            bookContent = {
                                BookScreen(
                                    books = books,
                                    focusId = destination.focusId.takeIf { destination.focusKind == SearchKind.BOOK },
                                    // 책마다 그 책에서 뽑은 글귀가 몇 개인지 세어 카드에 보여 줍니다.
                                    quoteCountBySyncId = remember(allItems) {
                                        allItems
                                            .filter { it.bookSyncId.isNotBlank() }
                                            .groupingBy { it.bookSyncId }
                                            .eachCount()
                                    },
                                    onSaveBook = { draft ->
                                        coroutineScope.launch {
                                            val result = viewModel.saveBook(draft)
                                            snackbarHostState.showSnackbar(
                                                if (result.isSuccess) "책을 저장했습니다."
                                                else result.exceptionOrNull()?.message ?: "책을 저장하지 못했습니다."
                                            )
                                        }
                                    },
                                    onUpdateProgress = { id, page ->
                                        coroutineScope.launch {
                                            val result = viewModel.updateBookProgress(id, page)
                                            if (result.isFailure) {
                                                snackbarHostState.showSnackbar(
                                                    result.exceptionOrNull()?.message ?: "진도를 기록하지 못했습니다."
                                                )
                                            }
                                        }
                                    },
                                    onToggleFinished = { id ->
                                        coroutineScope.launch { viewModel.toggleBookFinished(id) }
                                    },
                                    onDeleteBook = { id ->
                                        coroutineScope.launch {
                                            val result = viewModel.deleteBook(id)
                                            snackbarHostState.showSnackbar(
                                                if (result.isSuccess) "책을 지웠습니다."
                                                else result.exceptionOrNull()?.message ?: "책을 지우지 못했습니다."
                                            )
                                        }
                                    },
                                    onExtractQuote = { id, body, page ->
                                        coroutineScope.launch {
                                            val result = viewModel.extractQuoteFromBook(id, body, page)
                                            snackbarHostState.showSnackbar(
                                                if (result.isSuccess) "보관함에 글귀를 넣었습니다."
                                                else result.exceptionOrNull()?.message ?: "글귀를 넣지 못했습니다."
                                            )
                                        }
                                    }
                                )
                            },
                            quoteContent = {
                        LibraryScreen(
                            items = allItems,
                            categories = categories,
                            confirmedTodayIds = confirmedTodayIds,
                            shuffleSeed = shuffleSeed,
                            onShuffle = { viewModel.reshuffleContent() },
                            onToggleFavorite = { item ->
                                viewModel.toggleFavorite(item.id, !item.isFavorite)
                            },
                            // 스와이프로 넘길 때마다 스낵바가 뜨면 콤보 알약과 겹쳐 시끄럽습니다.
                            onConfirmItem = { item ->
                                toggleConfirmed(item, showMessage = false)
                            },
                            onOpenItem = { item ->
                                openItemDetail(AppTab.LIBRARY, item.id)
                            },
                            onAddContent = { openFreshAddForm() },
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
                            }
                        )

                        AppTab.ROUTINE -> RoutineScreen(
                            modifier = Modifier.padding(innerPadding),
                            focusId = destination.focusId.takeIf { destination.focusKind == SearchKind.ROUTINE },
                            routines = routines,
                            todayCounts = routineTodayCounts,
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
                                        "메모를 저장하고 수행 처리했습니다."
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
                            },
                            onMoveRoutine = { routine, up ->
                                coroutineScope.launch {
                                    val result = viewModel.moveRoutine(routine.id, up)
                                    // 성공하면 목록이 곧바로 움직여 보이므로 따로 알리지 않는다.
                                    result.exceptionOrNull()?.let { failure ->
                                        snackbarHostState.showSnackbar(
                                            failure.message ?: "루틴 순서를 바꾸지 못했습니다."
                                        )
                                    }
                                }
                            },
                            onMoveRoutineTo = { routine, targetIndex ->
                                coroutineScope.launch {
                                    val result = viewModel.moveRoutineTo(routine.id, targetIndex)
                                    result.exceptionOrNull()?.let { failure ->
                                        snackbarHostState.showSnackbar(
                                            failure.message ?: "루틴 순서를 바꾸지 못했습니다."
                                        )
                                    }
                                }
                            }
                        )

                        AppTab.TODO -> TodoScreen(
                            modifier = Modifier.padding(innerPadding),
                                    todos = todos,
                                    focusId = destination.focusId.takeIf { destination.focusKind == SearchKind.TODO },
                                    today = LocalDate.now(),
                                    canScheduleExactAlarms = canScheduleExactAlarms,
                                    onSaveTodo = { draft ->
                                        coroutineScope.launch {
                                            val result = viewModel.saveTodo(draft)
                                            result.exceptionOrNull()?.let { failure ->
                                                snackbarHostState.showSnackbar(
                                                    failure.message ?: "할 일을 저장하지 못했습니다."
                                                )
                                            }
                                        }
                                    },
                                    onToggleDone = { id ->
                                        coroutineScope.launch { viewModel.toggleTodoDone(id) }
                                    },
                                    onDeleteTodo = { id ->
                                        coroutineScope.launch {
                                            val result = viewModel.deleteTodo(id)
                                            val message = if (result.isSuccess) {
                                                "할 일을 지웠습니다."
                                            } else {
                                                result.exceptionOrNull()?.message ?: "할 일을 지우지 못했습니다."
                                            }
                                            snackbarHostState.showSnackbar(message)
                                        }
                                    },
                                    onOpenExactAlarmSettings = {
                                        viewModel.exactAlarmSettingsIntent()?.let(context::startActivity)
                                    }
                        )

                        AppTab.DIARY -> DiaryScreen(
                            modifier = Modifier.padding(innerPadding),
                            diaries = diaries,
                            locked = diaryLockEnabled,
                            // 기기에 이미 있는 잠금을 빌려 씁니다. 못 물어보는 기기면 그냥 엽니다.
                            // 열 수 없게 두면 사용자가 자기 일기에서 잠깁니다.
                            onRequestUnlock = { onUnlocked ->
                                val host = activity
                                if (host == null) onUnlocked() else AppLock.authenticate(
                                    activity = host,
                                    onUnlocked = onUnlocked,
                                    onFailed = { },
                                    title = "일기 열기"
                                )
                            },
                            focusId = destination.focusId.takeIf { destination.focusKind == SearchKind.DIARY },
                            today = LocalDate.now(),
                            // 서버가 보관하는 첨부를 받아오려면 주소와 키가 필요합니다.
                            serverUrl = serverSyncSettings.serverUrl,
                            apiKey = serverSyncSettings.apiKey,
                            onSaveDiary = { draft ->
                                coroutineScope.launch {
                                    val result = viewModel.saveDiary(draft)
                                    val message = if (result.isSuccess) {
                                        "일기를 저장했습니다."
                                    } else {
                                        result.exceptionOrNull()?.message ?: "일기를 저장하지 못했습니다."
                                    }
                                    snackbarHostState.showSnackbar(message)
                                }
                            },
                            onOpenGallery = { pushRoute(tabRoute(AppTab.GALLERY)) },
                            onDeleteDiary = { id ->
                                coroutineScope.launch {
                                    val result = viewModel.deleteDiary(id)
                                    val message = if (result.isSuccess) {
                                        "일기를 지웠습니다."
                                    } else {
                                        result.exceptionOrNull()?.message ?: "일기를 지우지 못했습니다."
                                    }
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        )

                        AppTab.SEARCH -> SearchScreen(
                            modifier = Modifier.padding(innerPadding),
                            items = allItems,
                            diaries = diaries,
                            todos = todos,
                            books = books,
                            routines = routines,
                            onOpenHit = { hit ->
                                if (hit.kind == SearchKind.QUOTE) {
                                    openItemDetail(AppTab.SEARCH, hit.id)
                                } else {
                                    // 나머지는 각자 사는 탭으로 데려가 그 항목을 짚어 줍니다.
                                    pushRoute(focusRoute(hit.kind, hit.id))
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

                        AppTab.GROWTH -> GrowthFeedbackScreen(
                            modifier = Modifier.padding(innerPadding),
                            reports = growthReports,
                            running = growthRunning,
                            errorMessage = growthError.ifBlank { aiFeedbackSettings.lastError },
                            previewText = growthPreview,
                            onDismissPreview = { growthPreview = null },
                            onRequest = { period ->
                                coroutineScope.launch {
                                    growthRunning = true
                                    growthError = ""
                                    val result = viewModel.requestGrowthFeedback(period)
                                    growthRunning = false
                                    result.onFailure { failure ->
                                        growthError = failure.message ?: "돌아보기를 받지 못했습니다."
                                    }
                                    result.onSuccess {
                                        snackbarHostState.showSnackbar("새 돌아보기가 도착했습니다.")
                                    }
                                }
                            },
                            onPreview = { period ->
                                coroutineScope.launch {
                                    viewModel.previewGrowthPrompt(period)
                                        .onSuccess { growthPreview = it }
                                        .onFailure { growthError = it.message ?: "미리 볼 수 없습니다." }
                                }
                            },
                            pendingPrompt = pendingGrowthPrompt,
                            onPromptCopied = { period ->
                                coroutineScope.launch {
                                    viewModel.markGrowthPromptCopied(period)
                                    snackbarHostState.showSnackbar(
                                        "복사했습니다. 채팅창에 붙여넣고 받은 답을 다시 가져오세요."
                                    )
                                }
                            },
                            onSaveAnswer = { text ->
                                coroutineScope.launch {
                                    val result = viewModel.saveGrowthAnswer(text)
                                    result.onSuccess { report ->
                                        snackbarHostState.showSnackbar(
                                            if (report.strengths.isEmpty() && report.improvements.isEmpty()) {
                                                "형식이 달라 가이드에 통째로 담았습니다."
                                            } else {
                                                "받아 온 답을 남겼습니다."
                                            }
                                        )
                                    }
                                    result.onFailure { failure ->
                                        growthError = failure.message ?: "답을 남기지 못했습니다."
                                    }
                                }
                            },
                            onDeleteReport = { report ->
                                coroutineScope.launch { viewModel.deleteGrowthReport(report.id) }
                            },
                            onKeepQuote = { report ->
                                coroutineScope.launch {
                                    val result = viewModel.keepSuggestedQuote(report)
                                    snackbarHostState.showSnackbar(
                                        if (result.isSuccess) "보관함에 담았습니다."
                                        else result.exceptionOrNull()?.message ?: "담지 못했습니다."
                                    )
                                }
                            },
                            onKeepRoutine = { title ->
                                coroutineScope.launch {
                                    val result = viewModel.keepSuggestedRoutine(title)
                                    snackbarHostState.showSnackbar(
                                        if (result.isSuccess) "루틴에 추가했습니다."
                                        else result.exceptionOrNull()?.message ?: "추가하지 못했습니다."
                                    )
                                }
                            },
                            onOpenSettings = { pushRoute(tabRoute(AppTab.SETTINGS)) }
                        )

                        AppTab.GALLERY -> AttachmentGalleryScreen(
                            modifier = Modifier.padding(innerPadding),
                            shots = attachmentShots,
                            serverUrl = serverSyncSettings.serverUrl,
                            apiKey = serverSyncSettings.apiKey,
                            // 누른 사진이 붙어 있던 기록으로 데려갑니다. 어디에 있던 것인지
                            // 알려만 주면 사용자가 다시 찾아 들어가야 합니다.
                            onOpenSource = { shot ->
                                when (shot.source) {
                                    SearchKind.QUOTE -> openItemDetail(AppTab.GALLERY, shot.sourceId)
                                    else -> pushRoute(focusRoute(shot.source, shot.sourceId))
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
                                                selectTab(AppTab.HOME)
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
                            syncStatus = syncStatus,
                            categories = categories,
                            syncBackups = syncBackups,
                            syncBackupDirectory = syncBackupDirectory,
                            notificationsBlocked = notificationsBlocked,
                            onSettingsChanged = viewModel::updateSettings,
                            onOpenHistory = { pushRoute(tabRoute(AppTab.HISTORY)) },
                            appLockEnabled = appLockEnabled,
                            onAppLockChanged = { enabled -> viewModel.setAppLockEnabled(enabled) },
                            diaryLockEnabled = diaryLockEnabled,
                            onDiaryLockChanged = { enabled -> viewModel.setDiaryLockEnabled(enabled) },
                            aiFeedbackSettings = aiFeedbackSettings,
                            onAiFeedbackSettingsChanged = { updated -> viewModel.updateAiFeedbackSettings(updated) },
                            dailySteps = dailySteps,
                            onDailyStepsChanged = { updated -> viewModel.setDailySteps(updated) },
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
                            onMergeFileRequested = { uri ->
                                coroutineScope.launch {
                                    val result = viewModel.mergeFromFile(uri)
                                    val message = if (result.isSuccess) {
                                        val counts = result.getOrThrow().counts
                                        "합친 결과: 항목 ${counts.itemCount}개, 루틴 ${counts.routineCount}개, " +
                                            "일기 ${counts.diaryCount}개, 할 일 ${counts.todoCount}개"
                                    } else {
                                        result.exceptionOrNull()?.message ?: "파일과 합치지 못했습니다."
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
                            onSyncWithServer = {
                                coroutineScope.launch {
                                    val result = viewModel.syncWithServer()
                                    val message = if (result.isSuccess) {
                                        val merged = result.getOrThrow().counts
                                        "병합 완료: 항목 ${merged.itemCount}개, 이력 ${merged.eventCount}개, 루틴 ${merged.routineCount}개. 직전 기기 데이터는 백업에 저장했습니다."
                                    } else {
                                        result.exceptionOrNull()?.message ?: "서버와 병합하지 못했습니다."
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

/** 검색 결과로 가는 길. 어느 탭인지는 종류가 정합니다. */
private fun focusRoute(kind: SearchKind, id: Long): String = "focus:${kind.name}:$id"

/** 그 종류를 어디서 보는지. 할 일과 루틴은 같은 탭 안에서 나뉩니다. */
private fun tabOf(kind: SearchKind): AppTab = when (kind) {
    SearchKind.QUOTE -> AppTab.LIBRARY
    SearchKind.BOOK -> AppTab.LIBRARY
    SearchKind.DIARY -> AppTab.DIARY
    SearchKind.TODO -> AppTab.TODO
    SearchKind.ROUTINE -> AppTab.ROUTINE
}

private fun parseRoute(route: String): AppDestination {
    val parts = route.split(":")
    return when (parts.firstOrNull()) {
        "tab" -> AppDestination(tab = AppTab.valueOf(parts[1]))
        "add" -> AppDestination(tab = AppTab.ADD)
        "focus" -> {
            val kind = SearchKind.valueOf(parts[1])
            AppDestination(
                tab = tabOf(kind),
                focusKind = kind,
                focusId = parts.getOrNull(2)?.toLongOrNull()
            )
        }
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
