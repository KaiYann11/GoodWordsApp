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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.codex.appgoodwords.BuildConfig
import java.io.File
import com.codex.appgoodwords.data.AppUpdateInstaller
import com.codex.appgoodwords.data.ContentDraft
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.BookProgressCalculator
import com.codex.appgoodwords.data.DailyCoachLine
import com.codex.appgoodwords.data.DayMood
import com.codex.appgoodwords.data.DailyStep
import com.codex.appgoodwords.data.ContentType
import com.codex.appgoodwords.data.ExposureEventEntity
import com.codex.appgoodwords.data.ExposureEventType
import com.codex.appgoodwords.data.ExposureTrigger
import com.codex.appgoodwords.data.GrowthReportEntity
import com.codex.appgoodwords.data.ReportPeriod
import com.codex.appgoodwords.data.RoutineStatsCalculator
import com.codex.appgoodwords.data.SearchKind
import com.codex.appgoodwords.ui.screen.AddContentScreen
import com.codex.appgoodwords.ui.screen.AppLock
import com.codex.appgoodwords.ui.screen.AppLockScreen
import com.codex.appgoodwords.ui.screen.AppLockState
import com.codex.appgoodwords.ui.screen.BookProgressScreen
import com.codex.appgoodwords.ui.screen.BookReviewScreen
import com.codex.appgoodwords.ui.screen.PrincipleScreen
import com.codex.appgoodwords.ui.screen.RoutineTab
import com.codex.appgoodwords.ui.screen.AttachmentGalleryScreen
import com.codex.appgoodwords.ui.screen.BookScreen
import com.codex.appgoodwords.ui.screen.DailyCoachTalkScreen
import com.codex.appgoodwords.ui.screen.DaySummaryDialog
import com.codex.appgoodwords.ui.screen.DetailScreen
import com.codex.appgoodwords.ui.screen.ClassifyInboxDialog
import com.codex.appgoodwords.ui.screen.TextCollectionScreen
import com.codex.appgoodwords.ui.screen.NotToDoScreen
import com.codex.appgoodwords.ui.screen.RoutineTabsScreen
import com.codex.appgoodwords.ui.screen.DiaryScreen
import com.codex.appgoodwords.ui.screen.DiaryUnlockCard
import com.codex.appgoodwords.ui.screen.GrowthFeedbackScreen
import com.codex.appgoodwords.ui.screen.HistoryScreen
import com.codex.appgoodwords.ui.screen.GoalDetailScreen
import com.codex.appgoodwords.ui.screen.GoalTab
import com.codex.appgoodwords.ui.screen.GoalTabsScreen
import com.codex.appgoodwords.ui.screen.HomeScreen
import com.codex.appgoodwords.ui.screen.IdeaCaptureDialog
import com.codex.appgoodwords.ui.screen.IdeaCaptureField
import com.codex.appgoodwords.ui.screen.LibraryScreen
import com.codex.appgoodwords.ui.screen.LibraryTab
import com.codex.appgoodwords.ui.screen.LibraryTabsScreen
import com.codex.appgoodwords.ui.screen.RoutineScreen
import com.codex.appgoodwords.ui.screen.RoutineStatsScreen
import com.codex.appgoodwords.ui.screen.SearchScreen
import com.codex.appgoodwords.ui.screen.SettingsScreen
import com.codex.appgoodwords.ui.screen.SettingsSection
import com.codex.appgoodwords.ui.screen.TodoScreen
import com.codex.appgoodwords.ui.theme.AppGoodWordsTheme
import com.codex.appgoodwords.work.AppNotifications
import com.codex.appgoodwords.work.DailyCoachSchedule
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
    /**
     * 이상향·목표·롤모델(사용자가 정함, 2026-10-29). 할 일이나 메모와 섞이지 않게 제 칸을 둡니다 — 끝내는 것도 적어 두고
     * 마는 것도 아니라 자꾸 마주치고 되새기는 것입니다. 하단 바의 여섯째입니다.
     */
    GOALS("목표"),
    /** 할 일은 루틴과 성격이 달라 자리를 나눴습니다. 루틴은 매일 세는 것이고 할 일은 한 번 끝나는 것입니다. */
    TODO("할 일"),
    DIARY("일기"),
    ADD("추가"),
    /** 이력은 매일 볼 화면이 아니라 설정 안으로 옮겼습니다. 하단 바에는 없습니다. */
    HISTORY("이력"),
    /** 독후감. 책 목록에서 엽니다. 하단 바에는 없습니다. */
    BOOK_REVIEW("독후감"),
    /** 날짜별 독서 진행. 책 카드의 진도 줄에서 엽니다. */
    BOOK_PROGRESS("독서 진행률"),
    /** 루틴 하나의 연속 날수와 요일. 루틴 카드의 기록 줄에서 엽니다. */
    ROUTINE_STATS("루틴 기록"),
    /** 이상향·목표·롤모델 하나. 목표 탭의 카드나 홈의 한 줄에서 엽니다. 하단 바에는 없습니다. */
    GOAL_DETAIL("목표"),
    /** AI 돌아보기는 홈 카드에서 엽니다. 하단 바에 자리가 없습니다. */
    GROWTH("AI 돌아보기"),
    /**
     * 오늘의 한마디와 나눈 말(사용자가 정함, 2026-10-29). 홈 한마디 카드의 "답하기"나 코치의 답 알림에서 엽니다.
     * 메신저처럼 입력 칸이 자판 바로 위에 붙도록 하단 바를 두지 않습니다.
     */
    COACH_TALK("오늘의 한마디"),
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
     * 담는 화면을 어느 종류로 열었는지.
     *
     * 메모·개념처럼 보관함 안에서 "여기에 직접 쓰기"로 들어온 경우입니다. 비워 두면 예전처럼
     * 주소를 보고 알아서 정합니다.
     */
    val newItemType: ContentType? = null,
    /**
     * 검색에서 고른 기록. 그 탭으로 가서 해당 항목까지 데려다줍니다.
     *
     * 탭만 바꿔 놓으면 찾아 놓고도 사용자가 다시 손으로 뒤져야 합니다.
     */
    val focusKind: SearchKind? = null,
    val focusId: Long? = null,
    /** 독후감을 펼친 책. 책 목록에서 열립니다. */
    val reviewBookId: Long? = null,
    /** 날짜별 진행을 펼친 책. */
    val progressBookId: Long? = null,
    /** 쌓인 기록을 펼친 루틴. */
    val statsRoutineId: Long? = null,
    /** 펼친 이상향·목표·롤모델. */
    val goalId: Long? = null
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    // 루틴마다 쌓인 것(연속 날수·요일). 저장하지 않고 체크 기록에서 그때그때 셉니다.
    val routineStats = remember(routines, routineChecks) {
        RoutineStatsCalculator.buildAll(routines, routineChecks, LocalDate.now())
    }
    val contentMemos by viewModel.contentMemos.collectAsStateWithLifecycle()
    val scoreTrend by viewModel.scoreTrend.collectAsStateWithLifecycle()
    val todayScore by viewModel.todayScore.collectAsStateWithLifecycle()
    val openedDayDigest by viewModel.openedDayDigest.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val feedbackNotes by viewModel.feedbackNotes.collectAsStateWithLifecycle()
    val onThisDay by viewModel.onThisDay.collectAsStateWithLifecycle()
    val moodPractice by viewModel.moodPractice.collectAsStateWithLifecycle()
    val attachmentShots by viewModel.attachmentShots.collectAsStateWithLifecycle()
    val growthReports by viewModel.growthReports.collectAsStateWithLifecycle()
    val aiFeedbackSettings by viewModel.aiFeedbackSettings.collectAsStateWithLifecycle()
    val dailyCoach by viewModel.dailyCoach.collectAsStateWithLifecycle()
    val coachFingerprint by viewModel.coachFingerprint.collectAsStateWithLifecycle()
    val coachLoading by viewModel.coachLoading.collectAsStateWithLifecycle()
    val coachReplying by viewModel.coachReplying.collectAsStateWithLifecycle()
    val routineNudge by viewModel.routineNudge.collectAsStateWithLifecycle()
    val pendingGrowthPrompt by viewModel.pendingGrowthPrompt.collectAsStateWithLifecycle()
    val moodLogs by viewModel.moodLogs.collectAsStateWithLifecycle()
    val captureIdeaRequest by viewModel.captureIdeaRequest.collectAsStateWithLifecycle()
    // 위젯의 적기로 들어왔거나 홈의 "아이디어 담기"를 눌렀을 때 뜹니다.
    var capturingIdea by remember { mutableStateOf(false) }
    val homeQuote by viewModel.homeQuote.collectAsStateWithLifecycle()
    val goalTracks by viewModel.goalTracks.collectAsStateWithLifecycle()
    val homeGoalLine by viewModel.homeGoalLine.collectAsStateWithLifecycle()
    val openDailyCoachRequest by viewModel.openDailyCoachRequest.collectAsStateWithLifecycle()
    val openDailyCoachTalkRequest by viewModel.openDailyCoachTalkRequest.collectAsStateWithLifecycle()
    // 홈이 한마디 칸을 열어 보여 주면 거둡니다.
    var coachFocusRequested by remember { mutableStateOf(false) }

    /**
     * 설정에서 보고 있는 갈래. null이면 갈래 목록입니다.
     *
     * 설정 화면이 아니라 여기서 들고 있습니다. 위쪽 막대의 제목이 갈래 이름이 되고, 막대의 ←가 설정을 닫기 전에
     * 갈래 목록으로 먼저 돌아가야 해서입니다.
     */
    var settingsSection by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
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
    val bookReadingLogs by viewModel.bookReadingLogs.collectAsStateWithLifecycle()
    val appUpdateState by viewModel.appUpdateState.collectAsStateWithLifecycle()
    val shuffleSeed by viewModel.shuffleSeed.collectAsStateWithLifecycle()
    val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val diaryLockEnabled by viewModel.diaryLockEnabled.collectAsStateWithLifecycle()

    // 기기 인증을 할 수 없는 환경에서는 잠긴 내용을 열지 않습니다.
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
    var classifyingItemId by rememberSaveable { mutableStateOf<Long?>(null) }
    var classifyingItem by remember { mutableStateOf(false) }
    var askingConceptId by remember { mutableStateOf<Long?>(null) }
    var confirmFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var confirmFeedbackToken by remember { mutableLongStateOf(0L) }

    val destination = remember(navStack) { parseRoute(navStack.last()) }
    val currentTab = destination.tab
    var diaryUnlocked by remember(navStack.last(), diaryLockEnabled) { mutableStateOf(false) }
    val diaryLocked = diaryLockEnabled && !diaryUnlocked
    DisposableEffect(lifecycleOwner, navStack.last(), diaryLockEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) diaryUnlocked = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
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

    fun unlockDiary(onUnlocked: () -> Unit = {}) {
        val host = activity
        if (host == null) {
            coroutineScope.launch { snackbarHostState.showSnackbar("기기 인증을 사용할 수 없어 일기를 열지 않았습니다.") }
            return
        }
        AppLock.authenticate(
            activity = host,
            onUnlocked = { diaryUnlocked = true; onUnlocked() },
            onFailed = { reason -> coroutineScope.launch { snackbarHostState.showSnackbar(reason) } },
            title = "일기 열기"
        )
    }

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

    /** 홈의 한마디 칸으로 데려갑니다. 홈만 열면 글귀 아래 "오늘" 칸이 보여 한마디는 옆 칸에 숨어 있습니다. */
    fun openDailyCoach() {
        selectTab(AppTab.HOME)
        coachFocusRequested = true
    }

    /**
     * 대화 화면으로 데려갑니다(코치의 답 알림·"코치가 답했습니다 · 보기"). 뒤로 가면 홈의 한마디 칸입니다 — 카드의 "답하기"로
     * 들어온 것과 같은 자리로 돌아가야 어디서 왔는지 헷갈리지 않습니다.
     */
    fun openDailyCoachTalk() {
        if (currentTab == AppTab.COACH_TALK) return
        navStack = listOf(tabRoute(AppTab.HOME), tabRoute(AppTab.COACH_TALK))
        coachFocusRequested = true
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

    /**
     * 번뜩인 것을 그 자리에서 담습니다.
     *
     * 담는 화면으로 데려가지 않습니다. 화면이 바뀌면 방금 떠오른 것을 붙들고 있어야 하는데,
     * 그 사이에 날아가는 것이 번뜩인 것입니다.
     */
    fun captureIdea(title: String) {
        coroutineScope.launch {
            val result = viewModel.captureIdea(title)
            snackbarHostState.showSnackbar(
                if (result.isSuccess) "보관함 아이디어에 담았습니다."
                else result.exceptionOrNull()?.message ?: "담지 못했습니다."
            )
        }
    }

    fun explainConcept(item: ContentItemEntity) {
        if (askingConceptId != null) return
        // 임시저장 글은 개념으로 옮겨 가고, 이미 개념인 것은 보던 자리에 그대로 있어야 합니다.
        val moving = item.type == ContentType.INBOX
        askingConceptId = item.id
        coroutineScope.launch {
            val result = viewModel.explainConcept(item.id)
            askingConceptId = null
            if (result.isSuccess) {
                val concept = result.getOrThrow()
                if (moving) {
                    // 답을 바로 보여 주고, 뒤로 가면 새 개념 탭에 머뭅니다.
                    navStack = listOf(
                        tabRoute(AppTab.LIBRARY),
                        focusRoute(SearchKind.CONCEPT, concept.id),
                        detailRoute(AppTab.LIBRARY, concept.id)
                    )
                }
                snackbarHostState.showSnackbar(
                    if (moving) "AI 설명과 함께 개념으로 옮겼습니다." else "AI 설명을 새로 받았습니다."
                )
            } else {
                snackbarHostState.showSnackbar(
                    result.exceptionOrNull()?.message ?: "AI에게 의미를 묻지 못했습니다."
                )
            }
        }
    }

    /** @param type 보관함에서 "직접 쓰기"로 들어왔을 때 그 탭의 종류. 비우면 주소를 보고 정합니다. */
    fun openFreshAddForm(type: ContentType? = null) {
        viewModel.clearSharedText()
        addFormVersion += 1
        val route = addRoute(type)
        if (navStack.lastOrNull() == route) {
            return
        }
        pushRoute(route)
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
        if (!sharedText.isNullOrBlank() && destination.editingItemId == null && !isAddRoute(navStack.last())) {
            pushRoute(addRoute())
        }
    }

    // 위젯의 적기로 들어오면 앱이 열리자마자 담는 칸이 떠야 합니다. 열어 놓고 다시 찾아
    // 들어가게 하면 폰 홈에서 한 번에 담으려던 뜻이 없어집니다.
    LaunchedEffect(captureIdeaRequest) {
        if (captureIdeaRequest) {
            capturingIdea = true
            viewModel.consumeCaptureIdeaRequest()
        }
    }

    // 알림을 눌러 왔으면 홈의 한마디 칸으로 데려갑니다.
    LaunchedEffect(openDailyCoachRequest) {
        if (openDailyCoachRequest) {
            openDailyCoach()
            viewModel.consumeOpenDailyCoachRequest()
        }
    }

    // 코치의 답 알림을 눌러 왔으면 그 대화로 데려갑니다. 답을 읽고 바로 이어 말할 수 있게.
    LaunchedEffect(openDailyCoachTalkRequest) {
        if (openDailyCoachTalkRequest) {
            openDailyCoachTalk()
            viewModel.consumeOpenDailyCoachTalkRequest()
        }
    }

    /**
     * 답을 기다리다 화면을 떠나 끊긴 말이 있으면, 앱이 화면에 돌아오는 대로 이어 받습니다.
     *
     * 배경 일꾼도 이어 받지만(설정의 *앱을 닫아도 받아 알림으로*를 켰을 때) 1분쯤 뒤입니다. 그 전에 돌아왔으면 기다리게 하지
     * 않습니다 — 둘이 겹쳐도 한 번만 묻습니다(코디네이터가 하나씩, 이미 답이 왔으면 묻지 않음).
     */
    LaunchedEffect(lifecycleOwner, dailyCoach.replyInterrupted) {
        if (!dailyCoach.replyInterrupted) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.retryCoachReply()
        }
    }

    /**
     * 홈을 보고 있는 동안, 코치에게 다시 물을 때인지 들여다봅니다 — 오늘 한 것이 바뀔 때와,
     * 홈을 열어 둔 채로 주기가 돌아올 때.
     *
     * **앱이 화면에 떠 있는 동안만 셉니다.** 앱을 나가도 이 화면은 남아 있어서, 예전에는 화면 밖에서도 주기가
     * 돌아오면 물었습니다 — Android 15가 화면 밖으로 나간 앱의 통신을 끊어 하루 횟수만 쓰고 실패했습니다.
     * 화면 밖은 배경 일꾼의 몫이고(`DailyCoachWorker`), 같은 규칙으로 묻습니다. 앱으로 돌아오면 처음부터
     * 다시 들여다봅니다. 실제로 물을지는 [MainViewModel.refreshDailyCoach]가 가립니다 — 대부분은 들여다보고
     * 지나갑니다.
     */
    // 내가 말을 걸었으면 그때부터 한 주기를 셉니다(lastActiveAt). 대화하는 중에 새 한마디가 끼어들지 않습니다.
    LaunchedEffect(lifecycleOwner, currentTab, coachFingerprint, dailyCoach.lastActiveAt, aiFeedbackSettings.gapMinutes) {
        if (currentTab != AppTab.HOME || coachFingerprint.isBlank()) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.refreshDailyCoach()
            delay(
                DailyCoachSchedule.delayMillis(
                    gapMinutes = aiFeedbackSettings.gapMinutes,
                    lastAskedAt = dailyCoach.lastActiveAt,
                    now = System.currentTimeMillis()
                )
            )
            viewModel.refreshDailyCoach()
        }
    }

    /**
     * 다른 탭에 있는 동안 새 한마디가 오면 아래에 알리고 데려갈 길을 줍니다.
     *
     * 홈에 있으면 카드가 알리므로 띄우지 않습니다. 앱을 막 열었을 때 이미 와 있던 것도 홈이 먼저 보여 줍니다.
     * 알림을 눌러 들어온 참이면 곧 카드로 가므로 띄우지 않습니다.
     */
    val newCoachTipAt = dailyCoach.tip?.createdAt?.takeIf { dailyCoach.hasNewTip && aiFeedbackSettings.coachEnabled }
    LaunchedEffect(newCoachTipAt) {
        // 둘 다 봅니다. 알림 요청을 거둔 뒤에 돌 수도, 거두기 전에 돌 수도 있습니다.
        // 대화 화면에 있으면 그 화면에 새 한마디가 끼어 보이므로 띄우지 않습니다.
        if (newCoachTipAt == null || currentTab == AppTab.HOME || currentTab == AppTab.COACH_TALK ||
            openDailyCoachRequest || coachFocusRequested
        ) {
            return@LaunchedEffect
        }
        val result = snackbarHostState.showSnackbar(
            message = "새 한마디가 도착했습니다",
            actionLabel = "보기",
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) openDailyCoach()
    }

    /**
     * 답을 기다리다 대화 화면을 떠났는데 그 사이 답이 오면 아래에 알리고 데려갈 길을 줍니다(새 한마디와 같은 방식).
     *
     * 앱이 뜨기 전에 온 답은 알리지 않습니다 — 그때는 알림이 이미 알렸고, 앱을 열 때마다 지난 답을 다시 알리게 됩니다.
     */
    val latestAnswerAt = dailyCoach.talk.lastOrNull { it.kind == DailyCoachLine.Kind.ANSWER }?.at ?: 0L
    val appearedAt = remember { System.currentTimeMillis() }
    var announcedAnswerAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(latestAnswerAt) {
        if (latestAnswerAt <= maxOf(appearedAt, announcedAnswerAt)) return@LaunchedEffect
        announcedAnswerAt = latestAnswerAt
        if (currentTab == AppTab.COACH_TALK || openDailyCoachTalkRequest) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "코치가 답했습니다",
            actionLabel = "보기",
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) openDailyCoachTalk()
    }

    LaunchedEffect(openItemRequest, allItems, currentTab) {
        val targetId = openItemRequest ?: return@LaunchedEffect
        if (allItems.any { it.id == targetId }) {
            pushRoute(detailRoute(currentTab, targetId))
            viewModel.consumeOpenItemRequest()
        }
    }

    AppGoodWordsTheme {
        when (lockState) {
            AppLockState.CHECKING -> {
                Box(modifier = Modifier.fillMaxSize())
                return@AppGoodWordsTheme
            }
            AppLockState.LOCKED -> {
                AppLockScreen(onUnlocked = { viewModel.unlock() })
                return@AppGoodWordsTheme
            }
            AppLockState.OPEN -> Unit
        }

        if (capturingIdea) {
            IdeaCaptureDialog(
                onCapture = { title -> captureIdea(title) },
                onDismiss = { capturingIdea = false }
            )
        }

        // 그날 요약. 홈의 점수 카드나 그래프 막대에서 엽니다.
        openedDayDigest?.let { digest ->
            DaySummaryDialog(
                digest = if (diaryLocked) digest.copy(
                    diaryTitles = if (digest.diaryTitles.isEmpty()) emptyList() else listOf("잠긴 일기 ${digest.diaryTitles.size}개")
                ) else digest,
                onMove = { date -> viewModel.openDay(date) },
                onDismiss = { viewModel.closeDay() }
            )
        }

        allItems.firstOrNull { it.id == classifyingItemId && it.type == ContentType.INBOX }?.let { item ->
            ClassifyInboxDialog(
                item = item,
                saving = classifyingItem,
                onDismiss = { classifyingItemId = null },
                onSelect = { target ->
                    classifyingItem = true
                    coroutineScope.launch {
                        val result = viewModel.classifyInboxItem(item.id, target)
                        classifyingItem = false
                        val message = if (result.isSuccess) {
                            classifyingItemId = null
                            when (target) {
                                ContentType.MEMO -> "메모로 옮겼습니다."
                                ContentType.IDEA -> "아이디어로 옮겼습니다."
                                else -> "글귀 모음으로 옮겼습니다."
                            }
                        } else result.exceptionOrNull()?.message ?: "옮기지 못했습니다."
                        snackbarHostState.showSnackbar(message)
                    }
                }
            )
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    title = {
                        Text(
                            text = when {
                                selectedItem != null -> selectedItem.title.ifBlank { "상세 보기" }
                                destination.editingItemId != null -> "항목 수정"
                                destination.newItemType == ContentType.CONCEPT -> "개념 적기"
                                destination.newItemType == ContentType.MEMO -> "메모 적기"
                                // 이상향·목표·롤모델 중 무엇인지가 제목입니다.
                                currentTab == AppTab.GOAL_DETAIL -> goalTracks
                                    .firstOrNull { it.goal.id == destination.goalId }
                                    ?.let { GoalTab.of(it.goal.type).label }
                                    ?: AppTab.GOAL_DETAIL.title
                                // 설정의 갈래 안에서는 그 갈래 이름이 제목입니다.
                                else -> settingsSection?.takeIf { currentTab == AppTab.SETTINGS }?.title
                                    ?: currentTab.title
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        if (canNavigateBack) {
                            IconButton(
                                onClick = {
                                    // 설정의 갈래 안이면 설정을 닫지 않고 갈래 목록으로 먼저 돌아갑니다.
                                    if (currentTab == AppTab.SETTINGS && settingsSection != null) {
                                        settingsSection = null
                                    } else {
                                        popRoute()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "뒤로 가기"
                                )
                            }
                        }
                    },
                    actions = {
                        // 대화 화면은 메신저처럼 말만 둡니다. 검색·설정은 나와서 엽니다.
                        val talking = currentTab == AppTab.COACH_TALK
                        // 검색은 특정 탭에 매이지 않습니다. 다섯 기능을 한 번에 찾기 때문입니다.
                        if (!talking && selectedItem == null && destination.editingItemId == null && currentTab != AppTab.SEARCH) {
                            IconButton(onClick = { pushRoute(tabRoute(AppTab.SEARCH)) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = "검색"
                                )
                            }
                        }
                        // 설정은 하단 바에서 빠졌으므로 어느 화면에서나 여기로 들어갑니다.
                        if (!talking && selectedItem == null && destination.editingItemId == null && currentTab != AppTab.SETTINGS) {
                            IconButton(
                                onClick = {
                                    // 설정은 늘 갈래 목록에서 엽니다. 지난번에 보던 갈래로 바로 떨어지면 어디인지 헷갈립니다.
                                    settingsSection = null
                                    pushRoute(tabRoute(AppTab.SETTINGS))
                                }
                            ) {
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
                // 대화 화면에는 하단 바를 두지 않습니다. 입력 칸이 자판 바로 위에 붙어야 합니다.
                if (selectedItem == null && currentTab != AppTab.COACH_TALK) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        // 담기(+)는 하단 바에 두지 않습니다. 하단 바는 "어디로 갈지"만 담아야
                        // 읽기 쉬운데, +만 혼자 "무엇을 할지"여서 성격이 달랐습니다.
                        // 지금은 보관함의 글귀 화면 안에 있습니다.
                        // 설정은 매일 쓰는 것이 아니라 위쪽 톱니로 옮겼습니다. 하단 바는 매일
                        // 들르는 다섯 곳만 남깁니다.
                        listOf(AppTab.HOME, AppTab.LIBRARY, AppTab.ROUTINE, AppTab.GOALS, AppTab.TODO, AppTab.DIARY)
                            .forEach { tab ->
                                val selected = currentTab == tab
                                // 새 한마디가 와 있는데 다른 탭에 있으면 홈에 점을 답니다(메신저 앱의 읽지 않은 표시처럼).
                                val coachDot = tab == AppTab.HOME && !selected &&
                                    aiFeedbackSettings.coachEnabled && dailyCoach.hasNewTip
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { selectTab(tab) },
                                    icon = {
                                        BadgedBox(badge = { if (coachDot) Badge() }) {
                                        Icon(
                                            imageVector = when (tab) {
                                                AppTab.HOME -> Icons.Outlined.Home
                                                AppTab.LIBRARY -> Icons.AutoMirrored.Outlined.LibraryBooks
                                                AppTab.ROUTINE -> Icons.Outlined.CheckCircle
                                                AppTab.GOALS, AppTab.GOAL_DETAIL -> Icons.Outlined.Explore
                                                AppTab.TODO -> Icons.AutoMirrored.Outlined.ListAlt
                                                AppTab.DIARY -> Icons.Outlined.EditNote
                                                AppTab.ADD -> Icons.Outlined.Add
                                                AppTab.HISTORY -> Icons.Outlined.History
                                                AppTab.BOOK_REVIEW -> Icons.Outlined.EditNote
                                                AppTab.BOOK_PROGRESS -> Icons.Outlined.AutoAwesome
                                                AppTab.ROUTINE_STATS -> Icons.Outlined.CheckCircle
                                                AppTab.GROWTH, AppTab.COACH_TALK -> Icons.Outlined.AutoAwesome
                                                AppTab.GALLERY -> Icons.Outlined.Image
                                                AppTab.SEARCH -> Icons.Outlined.Search
                                                AppTab.SETTINGS -> Icons.Outlined.Settings
                                            },
                                            contentDescription = null
                                        )
                                        }
                                    },
                                    label = { Text(tab.title, maxLines = 1) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                saveableStateHolder.SaveableStateProvider(navStack.last()) {
                if (selectedItem != null) {
                    DetailScreen(
                        modifier = Modifier.padding(innerPadding),
                        item = selectedItem,
                        onClassify = { classifyingItemId = selectedItem.id },
                        askingConcept = askingConceptId == selectedItem.id,
                        onAskConcept = { explainConcept(selectedItem) },
                        confirmedToday = selectedItem.id in confirmedTodayIds,
                        onEdit = { item ->
                            pushRoute(editRoute(currentTab, item.id))
                            addFormVersion += 1
                        },
                        // 이 글귀에 달린 것만 골라 넘깁니다. 화면은 자기 글귀 것만 그립니다.
                        memos = contentMemos.filter { it.contentItemId == selectedItem.id },
                        onSaveMemo = { body ->
                            coroutineScope.launch {
                                val result = viewModel.saveContentMemo(selectedItem.id, body)
                                if (result.isFailure) {
                                    snackbarHostState.showSnackbar(
                                        result.exceptionOrNull()?.message ?: "메모를 저장하지 못했습니다."
                                    )
                                }
                            }
                        },
                        onDeleteMemo = { memo ->
                            coroutineScope.launch {
                                val result = viewModel.deleteContentMemo(memo.id)
                                if (result.isFailure) {
                                    snackbarHostState.showSnackbar(
                                        result.exceptionOrNull()?.message ?: "메모를 지우지 못했습니다."
                                    )
                                }
                            }
                        },
                        // 기기 간 식별자는 syncId뿐입니다. 숫자 id로 이으면 다른 기기에서 엉뚱한 글귀가 됩니다.
                        routinesFromItem = routines.filter {
                            it.sourceContentSyncId.isNotBlank() &&
                                it.sourceContentSyncId == selectedItem.syncId
                        },
                        onMakeRoutine = { title ->
                            coroutineScope.launch {
                                val result = viewModel.makeRoutineFromQuote(selectedItem.id, title)
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
                            // 위젯·알림과 같은 글귀입니다. 읽음은 보관함에서 누르는 것과 같은 기록이라 오늘의 걸음을 채웁니다.
                            quote = homeQuote,
                            quoteConfirmedToday = homeQuote?.item?.id?.let { it in confirmedTodayIds } == true,
                            onConfirmQuote = { item -> toggleConfirmed(item, showMessage = true) },
                            onNextQuote = { viewModel.nextHomeQuote() },
                            onOpenQuote = { item -> openItemDetail(AppTab.HOME, item.id) },
                            onAddQuote = { openFreshAddForm() },
                            // 되고 싶은 나와 목표·롤모델 하나를 매일 마주칩니다. 누르면 그 기록으로.
                            goalLine = homeGoalLine,
                            onOpenGoal = { id -> pushRoute(goalRoute(id)) },
                            dailyLoop = dailyLoop,
                            // 걸음마다 그 화면으로 데려갑니다. 글귀 읽기는 홈 맨 위의 글귀로 올려 주므로 여기까지 오지 않습니다.
                            onOpenStep = { step ->
                                when (step) {
                                    DailyStep.QUOTE -> selectTab(AppTab.LIBRARY)
                                    DailyStep.ROUTINE -> selectTab(AppTab.ROUTINE)
                                    DailyStep.TODO -> selectTab(AppTab.TODO)
                                    DailyStep.DIARY -> selectTab(AppTab.DIARY)
                                    // 보관함 안의 독서 탭까지 데려갑니다. 보관함만 열면 글귀 탭이 나옵니다.
                                    DailyStep.READING -> {
                                        navStack = listOf(
                                            tabRoute(AppTab.LIBRARY),
                                            focusRoute(SearchKind.BOOK, 0L)
                                        )
                                    }
                                }
                            },
                            latestReport = growthReports.firstOrNull(),
                            onOpenGrowth = { pushRoute(tabRoute(AppTab.GROWTH)) },
                            // 켜 둔 사람에게만 카드를 둡니다. 끈 사람의 홈에는 자리를 내지 않습니다.
                            coach = dailyCoach.takeIf { aiFeedbackSettings.coachEnabled },
                            // 지문이 비어 있다는 것이 곧 "오늘 아직 아무것도 없다"는 뜻입니다.
                            // 점수의 셈(`todayScore.isEmpty`)과는 다릅니다 — 기분은 점수에
                            // 세지 않지만, 오늘 어땠는지는 코치가 말을 건넬 거리입니다.
                            coachHasRecordToday = coachFingerprint.isNotBlank(),
                            coachLoading = coachLoading,
                            coachReplying = coachReplying,
                            coachNudge = routineNudge,
                            // 앱을 닫아 두어도 알람이 깨워 받아 오는 경우에만 말합니다. 앱을 열어 둔 동안만 묻게 했으면
                            // 그 시각에 앱이 닫혀 있을 때 오지 않으니 약속하지 않습니다. 말을 주고받았으면 그때부터 셉니다.
                            coachNextAt = DailyCoachSchedule.nextAskAt(
                                gapMinutes = aiFeedbackSettings.gapMinutes,
                                limit = aiFeedbackSettings.askLimit,
                                askCount = dailyCoach.askCount,
                                lastAskedAt = dailyCoach.lastActiveAt
                            ).takeIf { aiFeedbackSettings.coachRunsInBackground && coachFingerprint.isNotBlank() },
                            onOpenRoutines = { selectTab(AppTab.ROUTINE) },
                            onRefreshCoach = { viewModel.refreshDailyCoach(force = true) },
                            onKeepCoachQuote = {
                                coroutineScope.launch {
                                    val result = viewModel.keepCoachQuote()
                                    snackbarHostState.showSnackbar(
                                        if (result.isSuccess) "보관함에 담았습니다."
                                        else result.exceptionOrNull()?.message ?: "담지 못했습니다."
                                    )
                                }
                            },
                            onKeepCoachRoutine = {
                                coroutineScope.launch {
                                    val result = viewModel.keepCoachRoutine()
                                    snackbarHostState.showSnackbar(
                                        if (result.isSuccess) "루틴에 추가했습니다."
                                        else result.exceptionOrNull()?.message ?: "추가하지 못했습니다."
                                    )
                                }
                            },
                            onKeepCoachAction = {
                                coroutineScope.launch {
                                    val result = viewModel.keepCoachAction()
                                    snackbarHostState.showSnackbar(
                                        if (result.isSuccess) "오늘 할 일에 담았습니다."
                                        else result.exceptionOrNull()?.message ?: "담지 못했습니다."
                                    )
                                }
                            },
                            onCoachSeen = { viewModel.markDailyCoachSeen() },
                            onReplyCoach = { pushRoute(tabRoute(AppTab.COACH_TALK)) },
                            coachFocusRequested = coachFocusRequested,
                            onCoachFocused = { coachFocusRequested = false },
                            // 오늘 점수는 흐름의 마지막 점입니다. 따로 셈하지 않고 같은 흐름에서 꺼냅니다.
                            todayScore = todayScore,
                            scoreTrend = scoreTrend,
                            onOpenDay = { date -> viewModel.openDay(date) },
                            memories = if (diaryLocked) onThisDay.filter { it.kind != SearchKind.DIARY } else onThisDay,
                            moodPractice = moodPractice,
                            todayMood = todayMood,
                            onOpenCapture = { capturingIdea = true },
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
                            inboxCount = allItems.count { it.type == ContentType.INBOX },
                            memoCount = allItems.count { it.type == ContentType.MEMO },
                            conceptCount = allItems.count { it.type == ContentType.CONCEPT },
                            inboxContent = {
                                TextCollectionScreen(
                                    items = allItems.filter { it.type == ContentType.INBOX },
                                    collectionType = ContentType.INBOX,
                                    onOpenItem = { item -> openItemDetail(AppTab.LIBRARY, item.id) },
                                    onClassifyItem = { item -> classifyingItemId = item.id },
                                    askingConceptId = askingConceptId,
                                    onAskConcept = { item -> explainConcept(item) },
                                    onPaste = { text ->
                                        coroutineScope.launch {
                                            val result = viewModel.captureInboxText(text)
                                            snackbarHostState.showSnackbar(
                                                result.fold(
                                                    onSuccess = { alreadyKept ->
                                                        if (alreadyKept) "임시저장소에 이미 있는 글입니다."
                                                        else "임시저장소에 담았습니다."
                                                    },
                                                    onFailure = { it.message ?: "담지 못했습니다." }
                                                )
                                            )
                                        }
                                    }
                                )
                            },
                            memoContent = {
                                TextCollectionScreen(
                                    items = allItems.filter { it.type == ContentType.MEMO },
                                    collectionType = ContentType.MEMO,
                                    onOpenItem = { item -> openItemDetail(AppTab.LIBRARY, item.id) },
                                    // 임시저장소를 거치지 않고 여기에 바로 적습니다.
                                    onAddNew = { openFreshAddForm(ContentType.MEMO) }
                                )
                            },
                            conceptContent = {
                                TextCollectionScreen(
                                    items = allItems.filter { it.type == ContentType.CONCEPT },
                                    collectionType = ContentType.CONCEPT,
                                    contentMemos = contentMemos,
                                    onOpenItem = { item -> openItemDetail(AppTab.LIBRARY, item.id) },
                                    askingConceptId = askingConceptId,
                                    // 직접 적어 둔 개념에도, 답이 아쉬운 개념에도 다시 물어봅니다.
                                    onAskConcept = { item -> explainConcept(item) },
                                    onAddNew = { openFreshAddForm(ContentType.CONCEPT) }
                                )
                            },
                            requestedTab = when (destination.focusKind) {
                                SearchKind.BOOK -> LibraryTab.BOOK
                                SearchKind.CONCEPT -> LibraryTab.CONCEPT
                                SearchKind.INBOX -> LibraryTab.INBOX
                                SearchKind.MEMO -> LibraryTab.MEMO
                                else -> null
                            },
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
                                    // 같은 날 여러 번 적어도 하루입니다(하루에 책 하나당 기록 하나).
                                    readingDaysBySyncId = remember(bookReadingLogs) {
                                        bookReadingLogs
                                            .filter { it.bookSyncId.isNotBlank() && it.readDate.isNotBlank() }
                                            .groupBy { it.bookSyncId }
                                            .mapValues { (_, logs) -> logs.map { it.readDate }.distinct().size }
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
                                    },
                                    onOpenReview = { id -> pushRoute(reviewRoute(id)) },
                                    onOpenProgress = { id -> pushRoute(progressRoute(id)) }
                                )
                            },
                            // 번뜩인 것은 아이디어 탭에서 봅니다. 글귀 목록에 섞으면 둘 다 흐려집니다.
                            ideaContent = {
                                LibraryScreen(
                                    items = remember(allItems) {
                                        allItems.filter { it.type == ContentType.IDEA }
                                    },
                                    categories = categories,
                                    confirmedTodayIds = confirmedTodayIds,
                                    showTypeFilter = false,
                                    header = {
                                        IdeaCaptureField(onCapture = { title -> captureIdea(title) })
                                    },
                                    onToggleFavorite = { item ->
                                        viewModel.toggleFavorite(item.id, !item.isFavorite)
                                    },
                                    onConfirmItem = { item -> toggleConfirmed(item, showMessage = false) },
                                    onOpenItem = { item -> openItemDetail(AppTab.LIBRARY, item.id) },
                                    onAddContent = { openFreshAddForm() },
                                    onResetTodayConfirmed = { }
                                )
                            },
                            quoteContent = {
                        LibraryScreen(
                            items = remember(allItems) { allItems.filter { it.type.isReadingContent } },
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

                        AppTab.ROUTINE -> RoutineTabsScreen(
                            modifier = Modifier.padding(innerPadding),
                            requestedTab = when (destination.focusKind) {
                                SearchKind.NOT_TO_DO -> RoutineTab.NOT_TO_DO
                                SearchKind.PRINCIPLE -> RoutineTab.PRINCIPLE
                                SearchKind.ROUTINE -> RoutineTab.ROUTINE
                                else -> null
                            },
                            requestKey = navStack.last(),
                            notToDoContent = {
                                NotToDoScreen(
                                    items = allItems,
                                    onSave = viewModel::saveContent,
                                    onDelete = viewModel::deleteContent,
                                    focusId = destination.focusId.takeIf { destination.focusKind == SearchKind.NOT_TO_DO }
                                )
                            },
                            principleContent = {
                                PrincipleScreen(
                                    items = allItems,
                                    onSave = viewModel::saveContent,
                                    onDelete = viewModel::deleteContent,
                                    focusId = destination.focusId.takeIf { destination.focusKind == SearchKind.PRINCIPLE }
                                )
                            },
                            routineContent = {
                                RoutineScreen(
                                    focusId = destination.focusId.takeIf { destination.focusKind == SearchKind.ROUTINE },
                                    routines = routines,
                                    todayCounts = routineTodayCounts,
                                    stats = routineStats,
                                    onOpenStats = { routine -> pushRoute(routineStatsRoute(routine.id)) },
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
                            locked = diaryLocked,
                            onRequestUnlock = { onUnlocked -> unlockDiary(onUnlocked) },
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
                            diaryLocked = diaryLocked,
                            onUnlockDiaries = { unlockDiary() },
                            items = allItems,
                            contentMemos = contentMemos,
                            diaries = diaries,
                            todos = todos,
                            books = books,
                            routines = routines,
                            routineMemos = routineMemos,
                            growthReports = growthReports,
                            onOpenHit = { hit ->
                                if (hit.kind in listOf(SearchKind.QUOTE, SearchKind.MEMO, SearchKind.INBOX, SearchKind.CONCEPT)) {
                                    openItemDetail(AppTab.SEARCH, hit.id)
                                } else if (hit.kind == SearchKind.GOAL) {
                                    // 목표는 목록에서 짚어 주기보다 그 기록을 바로 엽니다. 되새긴 기록까지 거기 있습니다.
                                    pushRoute(goalRoute(hit.id))
                                } else {
                                    // 나머지는 각자 사는 탭으로 데려가 그 항목을 짚어 줍니다.
                                    pushRoute(focusRoute(hit.kind, hit.id))
                                }
                            }
                        )

                        AppTab.BOOK_PROGRESS -> {
                            val book = books.firstOrNull { it.id == destination.progressBookId }
                            if (book == null) {
                                // 다른 기기에서 지운 책일 수 있습니다. 빈 화면을 두느니 목록으로 돌립니다.
                                LaunchedEffect(destination.progressBookId) { popRoute() }
                            } else {
                                BookProgressScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    book = book,
                                    // 저장하지 않고 그때그때 셉니다. 기록을 고치면 곧바로 따라옵니다.
                                    report = remember(book, bookReadingLogs) {
                                        BookProgressCalculator.build(book, bookReadingLogs)
                                    },
                                    today = LocalDate.now()
                                )
                            }
                        }

                        AppTab.GOALS -> GoalTabsScreen(
                            modifier = Modifier.padding(innerPadding),
                            tracks = goalTracks,
                            onOpen = { id -> pushRoute(goalRoute(id)) },
                            onSave = viewModel::saveGoal
                        )

                        AppTab.GOAL_DETAIL -> {
                            val track = goalTracks.firstOrNull { it.goal.id == destination.goalId }
                            if (track == null) {
                                // 막 적은 것은 목록이 따라오는 데 잠깐 걸립니다. 기다렸다가 끝내 없으면(다른 기기에서 지움) 목록으로.
                                LaunchedEffect(destination.goalId) {
                                    delay(GOAL_APPEAR_GRACE_MS)
                                    popRoute()
                                }
                            } else {
                                GoalDetailScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    track = track,
                                    routines = routines,
                                    onSaveText = { title, body -> viewModel.saveGoal(track.goal.id, track.goal.type, title, body) },
                                    onSetRoutines = { ids -> viewModel.setGoalRoutines(track.goal.id, ids) },
                                    onSetPinned = { pinned -> viewModel.setGoalPinned(track.goal.id, pinned) },
                                    onDelete = { viewModel.deleteContent(track.goal.id).onSuccess { popRoute() } },
                                    onReflect = { body -> viewModel.saveContentMemo(track.goal.id, body) },
                                    onDeleteReflection = { memoId -> viewModel.deleteContentMemo(memoId) },
                                    onOpenQuote = { quote -> openItemDetail(AppTab.GOALS, quote.id) },
                                    onOpenRoutines = { selectTab(AppTab.ROUTINE) },
                                    onMessage = { message -> coroutineScope.launch { snackbarHostState.showSnackbar(message) } }
                                )
                            }
                        }

                        AppTab.ROUTINE_STATS -> {
                            val routine = routines.firstOrNull { it.id == destination.statsRoutineId }
                            if (routine == null) {
                                // 다른 기기에서 지운 루틴일 수 있습니다. 빈 화면을 두느니 목록으로 돌립니다.
                                LaunchedEffect(destination.statsRoutineId) { popRoute() }
                            } else {
                                RoutineStatsScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    routine = routine,
                                    report = routineStats[routine.id]
                                        ?: RoutineStatsCalculator.build(routine, routineChecks, LocalDate.now()),
                                    today = LocalDate.now()
                                )
                            }
                        }

                        AppTab.BOOK_REVIEW -> {
                            val book = books.firstOrNull { it.id == destination.reviewBookId }
                            if (book == null) {
                                // 다른 기기에서 지운 책일 수 있습니다. 빈 화면을 두느니 목록으로 돌립니다.
                                LaunchedEffect(destination.reviewBookId) { popRoute() }
                            } else {
                                BookReviewScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    book = book,
                                    onSave = { review ->
                                        coroutineScope.launch {
                                            val result = viewModel.saveBookReview(book.id, review)
                                            snackbarHostState.showSnackbar(
                                                if (result.isSuccess) "독후감을 저장했습니다."
                                                else result.exceptionOrNull()?.message ?: "독후감을 저장하지 못했습니다."
                                            )
                                        }
                                    },
                                    onExtractQuote = { body, page ->
                                        coroutineScope.launch {
                                            val result = viewModel.extractQuoteFromBook(book.id, body, page)
                                            snackbarHostState.showSnackbar(
                                                if (result.isSuccess) "보관함에 글귀를 넣었습니다."
                                                else result.exceptionOrNull()?.message ?: "글귀를 넣지 못했습니다."
                                            )
                                        }
                                    }
                                )
                            }
                        }

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

                        AppTab.GROWTH -> if (diaryLocked) DiaryUnlockCard(
                            modifier = Modifier.padding(innerPadding).padding(16.dp),
                            onUnlock = { unlockDiary() }
                        ) else GrowthFeedbackScreen(
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
                            onDeleteReports = { ids ->
                                coroutineScope.launch {
                                    val result = viewModel.deleteGrowthReports(ids)
                                    snackbarHostState.showSnackbar(
                                        if (result.isSuccess) "${result.getOrDefault(0)}편을 정리했습니다."
                                        else result.exceptionOrNull()?.message ?: "정리하지 못했습니다."
                                    )
                                }
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
                            // 돌아보기에서 여는 설정은 AI 갈래입니다. 목록을 거쳐 다시 찾게 하지 않습니다.
                            onOpenSettings = {
                                settingsSection = SettingsSection.AI
                                pushRoute(tabRoute(AppTab.SETTINGS))
                            }
                        )

                        AppTab.COACH_TALK -> DailyCoachTalkScreen(
                            // 가장자리까지 그리는 창이라 자판이 올라와도 창이 줄지 않습니다(Android 15에서 확인, 2026-10-29) —
                            // 그대로 두면 맨 아래 입력 칸과 보내기가 자판에 깔립니다. 자판만큼 비키되, 아래 시스템 막대 몫은
                            // Scaffold가 이미 비켰으니 겹쳐 세지 않습니다(겹치면 입력 칸과 자판 사이에 빈 줄이 생깁니다).
                            modifier = Modifier
                                .padding(innerPadding)
                                .consumeWindowInsets(innerPadding)
                                .imePadding(),
                            state = dailyCoach,
                            replying = coachReplying,
                            canReply = aiFeedbackSettings.coachEnabled,
                            onSend = { text -> viewModel.replyToCoach(text) },
                            onRetry = { viewModel.retryCoachReply() },
                            onKeepAnswerAction = { at ->
                                coroutineScope.launch {
                                    val result = viewModel.keepCoachAnswerAction(at)
                                    snackbarHostState.showSnackbar(
                                        if (result.isSuccess) "오늘 할 일에 담았습니다."
                                        else result.exceptionOrNull()?.message ?: "담지 못했습니다."
                                    )
                                }
                            },
                            // 지금 한마디의 담기는 카드와 같은 길입니다. 한쪽에서 담으면 다른 쪽 버튼도 거둡니다.
                            onKeepQuote = {
                                coroutineScope.launch {
                                    val result = viewModel.keepCoachQuote()
                                    snackbarHostState.showSnackbar(
                                        if (result.isSuccess) "보관함에 담았습니다."
                                        else result.exceptionOrNull()?.message ?: "담지 못했습니다."
                                    )
                                }
                            },
                            onKeepRoutine = {
                                coroutineScope.launch {
                                    val result = viewModel.keepCoachRoutine()
                                    snackbarHostState.showSnackbar(
                                        if (result.isSuccess) "루틴에 추가했습니다."
                                        else result.exceptionOrNull()?.message ?: "추가하지 못했습니다."
                                    )
                                }
                            },
                            onKeepAction = {
                                coroutineScope.launch {
                                    val result = viewModel.keepCoachAction()
                                    snackbarHostState.showSnackbar(
                                        if (result.isSuccess) "오늘 할 일에 담았습니다."
                                        else result.exceptionOrNull()?.message ?: "담지 못했습니다."
                                    )
                                }
                            },
                            onSeen = { viewModel.markDailyCoachSeen() }
                        )

                        AppTab.GALLERY -> AttachmentGalleryScreen(
                            modifier = Modifier.padding(innerPadding),
                            diaryLocked = diaryLocked,
                            onUnlockDiaries = { unlockDiary() },
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
                            // 고칠 것이 없으면 빈 칸입니다. 보관함에서 종류를 짚어 들어왔으면
                            // 그 종류만 담아 둔 밑그림을 줍니다(담는 화면이 종류 고르개를 감춥니다).
                            initialDraft = editingItem?.let(ContentDraft::fromItem)
                                ?: destination.newItemType?.let { ContentDraft(type = it) },
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
                                            if (navStack.size > 1 && isAddRoute(navStack.last())) {
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

                        AppTab.SETTINGS -> if (diaryLocked) DiaryUnlockCard(
                            modifier = Modifier.padding(innerPadding).padding(16.dp),
                            onUnlock = { unlockDiary() }
                        ) else SettingsScreen(
                            modifier = Modifier.padding(innerPadding),
                            section = settingsSection,
                            onSectionChange = { settingsSection = it },
                            settings = settings,
                            currentVersionName = BuildConfig.VERSION_NAME,
                            currentVersionCode = BuildConfig.VERSION_CODE,
                            appUpdateState = appUpdateState,
                            onCheckAppUpdate = { viewModel.checkForAppUpdate(BuildConfig.VERSION_CODE) },
                            onDownloadAppUpdate = { viewModel.downloadAppUpdate() },
                            onInstallAppUpdate = { path ->
                                // 설치 화면을 띄우는 것은 화면 쪽 일입니다. 뷰모델에는 Context가 없습니다.
                                runCatching { AppUpdateInstaller.install(context, File(path)) }
                                    .onFailure { error ->
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                error.message ?: "설치 화면을 열지 못했습니다."
                                            )
                                        }
                                    }
                            },
                            onDismissAppUpdate = { viewModel.clearAppUpdateState() },
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
                            onDiaryLockChanged = { enabled ->
                                if (enabled) viewModel.setDiaryLockEnabled(true)
                                else unlockDiary { viewModel.setDiaryLockEnabled(false) }
                            },
                            aiFeedbackSettings = aiFeedbackSettings,
                            onAiFeedbackSettingsChanged = { updated -> viewModel.updateAiFeedbackSettings(updated) },
                            // 켜기 전에 무엇이 나가는지 눈으로 볼 수 있어야 합니다.
                            onPreviewCoachPrompt = { viewModel.previewDailyCoachPrompt() },
                            canScheduleExactAlarms = canScheduleExactAlarms,
                            onOpenExactAlarmSettings = {
                                viewModel.exactAlarmSettingsIntent()?.let(context::startActivity)
                            },
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

/** 종류를 짚어 열면 담는 화면이 그 종류로 저장합니다(보관함의 "직접 쓰기"). */
private fun addRoute(type: ContentType? = null): String = if (type == null) "add" else "add:${type.name}"

private fun isAddRoute(route: String): Boolean = route == "add" || route.startsWith("add:")

private fun detailRoute(tab: AppTab, itemId: Long): String = "detail:${tab.name}:$itemId"

/** 독후감은 긴 글이라 대화상자가 아니라 제 화면을 씁니다. */
private fun reviewRoute(bookId: Long): String = "review:$bookId"

/** 날짜별 진행. 책 카드의 진도 줄에서 엽니다. */
private fun progressRoute(bookId: Long): String = "progress:$bookId"

/** 루틴 하나의 연속 날수와 요일. 루틴 카드의 기록 줄에서 엽니다. */
private fun routineStatsRoute(routineId: Long): String = "routine-stats:$routineId"

/** 이상향·목표·롤모델 하나. 목표 탭의 카드나 홈의 한 줄에서 엽니다. */
private fun goalRoute(goalId: Long): String = "goal:$goalId"

/** 막 적은 목표가 목록에 따라오기를 기다리는 시간. 지나도 없으면 다른 기기에서 지운 것으로 보고 목록으로 돌립니다. */
private const val GOAL_APPEAR_GRACE_MS = 1_500L

private fun editRoute(returnTab: AppTab, itemId: Long): String = "edit:${returnTab.name}:$itemId"

/** 검색 결과로 가는 길. 어느 탭인지는 종류가 정합니다. */
private fun focusRoute(kind: SearchKind, id: Long): String = "focus:${kind.name}:$id"

/** 그 종류를 어디서 보는지. 할 일과 루틴은 같은 탭 안에서 나뉩니다. */
private fun tabOf(kind: SearchKind): AppTab = when (kind) {
    SearchKind.QUOTE, SearchKind.MEMO, SearchKind.INBOX, SearchKind.CONCEPT -> AppTab.LIBRARY
    SearchKind.BOOK -> AppTab.LIBRARY
    SearchKind.DIARY -> AppTab.DIARY
    SearchKind.TODO -> AppTab.TODO
    SearchKind.ROUTINE, SearchKind.NOT_TO_DO, SearchKind.PRINCIPLE -> AppTab.ROUTINE
    SearchKind.GOAL -> AppTab.GOALS
    SearchKind.GROWTH -> AppTab.GROWTH
}

private fun parseRoute(route: String): AppDestination {
    val parts = route.split(":")
    return when (parts.firstOrNull()) {
        "tab" -> AppDestination(tab = AppTab.valueOf(parts[1]))
        "add" -> AppDestination(
            tab = AppTab.ADD,
            // 모르는 이름이 와도 담는 길을 막지 않습니다. 그때는 예전처럼 알아서 정합니다.
            newItemType = parts.getOrNull(1)?.let { name -> ContentType.entries.firstOrNull { it.name == name } }
        )
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
        "review" -> AppDestination(
            tab = AppTab.BOOK_REVIEW,
            reviewBookId = parts.getOrNull(1)?.toLongOrNull()
        )
        "progress" -> AppDestination(
            tab = AppTab.BOOK_PROGRESS,
            progressBookId = parts.getOrNull(1)?.toLongOrNull()
        )
        "goal" -> AppDestination(
            tab = AppTab.GOAL_DETAIL,
            goalId = parts.getOrNull(1)?.toLongOrNull()
        )
        "routine-stats" -> AppDestination(
            tab = AppTab.ROUTINE_STATS,
            statsRoutineId = parts.getOrNull(1)?.toLongOrNull()
        )
        "edit" -> AppDestination(
            tab = AppTab.ADD,
            editingItemId = parts.getOrNull(2)?.toLongOrNull(),
            returnTabAfterEdit = AppTab.valueOf(parts[1])
        )
        else -> AppDestination(tab = AppTab.HOME)
    }
}
