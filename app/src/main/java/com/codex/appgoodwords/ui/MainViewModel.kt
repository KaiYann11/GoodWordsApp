package com.codex.appgoodwords.ui

import android.net.Uri
import android.os.SystemClock
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codex.appgoodwords.data.AI_CATEGORY
import com.codex.appgoodwords.data.AiFeedbackSettings
import com.codex.appgoodwords.data.AppContainer
import com.codex.appgoodwords.data.AppDataSnapshot
import com.codex.appgoodwords.data.AppUpdateInstaller
import com.codex.appgoodwords.data.AppUpdateJson
import com.codex.appgoodwords.data.AppUpdateState
import com.codex.appgoodwords.data.AppImportResult
import com.codex.appgoodwords.data.AttachmentGallery
import com.codex.appgoodwords.data.BookDraft
import com.codex.appgoodwords.data.BookEntity
import com.codex.appgoodwords.data.BookReadingLogEntity
import com.codex.appgoodwords.data.ContentDraft
import com.codex.appgoodwords.data.ConceptExplanation
import com.codex.appgoodwords.data.ContentNormalizer
import com.codex.appgoodwords.data.ContentType
import com.codex.appgoodwords.data.DiaryDraft
import com.codex.appgoodwords.data.DiaryEntity
import com.codex.appgoodwords.data.DiaryMood
import com.codex.appgoodwords.data.MoodLogEntity
import com.codex.appgoodwords.data.ExposureEventEntity
import com.codex.appgoodwords.data.ExposureTrigger
import com.codex.appgoodwords.data.RoutineCheckEntity
import com.codex.appgoodwords.data.FeedbackWriter
import com.codex.appgoodwords.data.GoalTracking
import com.codex.appgoodwords.data.GrowthReportEntity
import com.codex.appgoodwords.data.LinkMetadata
import com.codex.appgoodwords.data.MoodPractice
import com.codex.appgoodwords.data.OnThisDay
import com.codex.appgoodwords.data.ReminderSettings
import com.codex.appgoodwords.data.ReportPeriod
import com.codex.appgoodwords.data.RoutineDraft
import com.codex.appgoodwords.data.RoutineNudgeCalculator
import com.codex.appgoodwords.data.RoutineStatsCalculator
import com.codex.appgoodwords.data.ServerConnectionInfo
import com.codex.appgoodwords.data.ServerSyncResult
import com.codex.appgoodwords.data.ServerSyncSettings
import com.codex.appgoodwords.data.DailyCoachPrompt
import com.codex.appgoodwords.data.DailyCoachState
import com.codex.appgoodwords.data.DailyLoopCalculator
import com.codex.appgoodwords.data.DayDigest
import com.codex.appgoodwords.data.DayScore
import com.codex.appgoodwords.data.DayScoreCalculator
import com.codex.appgoodwords.data.ScoreTrend
import com.codex.appgoodwords.data.DailyStep
import com.codex.appgoodwords.data.StatsCalculator
import com.codex.appgoodwords.data.SyncBackup
import com.codex.appgoodwords.data.SyncBackupKind
import com.codex.appgoodwords.data.SyncStatus
import com.codex.appgoodwords.data.TodoDraft
import com.codex.appgoodwords.data.TodoEntity
import com.codex.appgoodwords.ui.screen.AppLockState
import com.codex.appgoodwords.ui.screen.HomeQuote
import com.codex.appgoodwords.widget.QuoteWidget
import com.codex.appgoodwords.work.AppNotifications
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainViewModel(
    private val container: AppContainer
) : ViewModel() {
    val allItems = container.repository.observeAllContent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 홈 맨 위의 글귀 — 위젯·알림과 같은 "지금의 글귀"([com.codex.appgoodwords.data.SettingsStore.widgetContentIdFlow]).
     *
     * 앱 이름이 "오늘의 글귀"인데 홈에 글귀가 없어 읽으려면 보관함까지 들어가야 했습니다. 고르는 규칙은 알림·위젯과
     * 같습니다 — 가장 오래 못 만난 것부터, 알림에 보낼 카테고리 안에서. 아직 읽는 중이면 null입니다
     * (빈 보관함으로 읽혀 "글귀를 담아 보세요"가 잠깐 비치지 않게 [allItems]가 아니라 DB를 바로 봅니다).
     */
    val homeQuote = combine(
        container.settingsStore.widgetContentIdFlow,
        container.repository.observeAllContent(),
        container.settingsStore.settingsFlow.map { it.categoryFilter }.distinctUntilChanged()
    ) { id, items, category ->
        val reading = items.filter { it.type.isReadingContent }
        val inCategory = reading.filter { category.isBlank() || it.category == category }
        HomeQuote(
            item = reading.firstOrNull { it.id == id },
            hasCandidates = inCategory.isNotEmpty(),
            filteredOut = reading.isNotEmpty() && inCategory.isEmpty()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val historyEvents = container.repository.observeExposureEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val routines = container.repository.observeRoutines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val diaries = container.repository.observeDiaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todos = container.repository.observeTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val books = container.repository.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 톡 찍어 둔 기분. 일기를 안 쓴 날에도 남습니다. */
    val moodLogs = container.repository.observeMoodLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 날짜별 독서 기록. 독서 걸음과 점수가 "언제 읽었는지"를 여기서 봅니다. */
    val bookReadingLogs = container.repository.observeBookReadingLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val growthReports = container.repository.observeGrowthReports()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val aiFeedbackSettings = container.settingsStore.aiFeedbackSettingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiFeedbackSettings())

    /** 복사해 두고 아직 답을 받아 적지 않은 물음. 없으면 null. */
    val pendingGrowthPrompt = container.settingsStore.pendingGrowthPromptFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val routineChecks = container.repository.observeRoutineChecks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val routineMemos = container.repository.observeRoutineMemos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 글귀 옆에 덧붙여 둔 메모. 상세 화면이 자기 글귀 것만 골라 씁니다. */
    val contentMemos = container.repository.observeContentMemos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 목표 탭 — 이상향·목표·롤모델마다 되새긴 기록, 이어 둔 루틴을 최근에 밟은 날, 롤모델의 글귀.
     *
     * 저장하지 않고 그때그때 셉니다([GoalTracking]). 루틴 하나의 날수는 루틴 기록 화면과 같은 셈
     * ([RoutineStatsCalculator])이라 두 화면이 다른 숫자를 말하지 않습니다.
     */
    val goalTracks = combine(allItems, contentMemos, routines, routineChecks) { items, memos, routineList, checks ->
        val today = LocalDate.now()
        GoalTracking.build(
            goals = items.filter { it.type.isGoal },
            memos = memos,
            routines = routineList,
            routineStats = RoutineStatsCalculator.buildAll(routineList, checks, today),
            items = items,
            today = today
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 홈 "오늘" 칸 맨 위의 한 줄. 목표 탭에 아무것도 없으면 null입니다. */
    val homeGoalLine = allItems
        .map { items -> GoalTracking.homeLine(items.filter { it.type.isGoal }, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // 다섯 가지를 다 봅니다. 글귀와 루틴만 세면 앱이 하는 일의 절반만 돌아보는 셈입니다.
    val stats = combine(
        historyEvents,
        allItems,
        routineChecks,
        combine(diaries, todos, books, moodLogs) { diaryList, todoList, bookList, logs ->
            StatsInputs(diaryList, todoList, bookList, logs)
        }
    ) { events, items, checks, extras ->
        StatsCalculator.build(
            events = events,
            items = items,
            routineChecks = checks,
            today = LocalDate.now(),
            diaries = extras.diaries,
            todos = extras.todos,
            books = extras.books,
            moodLogs = extras.moodLogs
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        StatsCalculator.build(
            events = emptyList(),
            items = emptyList(),
            routineChecks = emptyList(),
            today = LocalDate.now()
        )
    )

    /**
     * 오늘의 걸음. 홈 맨 위에 둡니다.
     *
     * 따로 저장하지 않고 이미 있는 기록에서 셉니다. "오늘 했는지"를 어딘가에 또 적어 두면
     * 이력을 지웠을 때 두 값이 어긋납니다.
     */
    val dailySteps = container.settingsStore.dailyStepsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyStep.DEFAULTS)

    val dailyLoop = combine(
        combine(historyEvents, routineChecks, todos, diaries, bookReadingLogs) { events, checks, todoList, diaryList, logs ->
            DayInputs(events, checks, todoList, diaryList, logs)
        },
        container.settingsStore.dailyStepsFlow
    ) { inputs, steps ->
        DailyLoopCalculator.build(
            events = inputs.events,
            routineChecks = inputs.routineChecks,
            todos = inputs.todos,
            diaries = inputs.diaries,
            today = LocalDate.now(),
            steps = steps,
            readingLogs = inputs.readingLogs
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DailyLoopCalculator.build(
            events = emptyList(),
            routineChecks = emptyList(),
            todos = emptyList(),
            diaries = emptyList(),
            today = LocalDate.now()
        )
    )

    fun setDailySteps(steps: List<DailyStep>) {
        viewModelScope.launch { container.settingsStore.setDailySteps(steps) }
    }

    /**
     * 최근 네 주의 점수 흐름.
     *
     * 저장하지 않고 그때그때 기록에서 셉니다. 하루의 축을 바꾸면 지난날 점수도 곧바로
     * 새 기준이 됩니다([DayScoreCalculator]).
     */
    val scoreTrend = combine(
        combine(historyEvents, routineChecks, todos, diaries, bookReadingLogs) { events, checks, todoList, diaryList, logs ->
            DayInputs(events, checks, todoList, diaryList, logs)
        },
        container.settingsStore.dailyStepsFlow
    ) { inputs, steps ->
        DayScoreCalculator.trend(
            today = LocalDate.now(),
            events = inputs.events,
            routineChecks = inputs.routineChecks,
            todos = inputs.todos,
            diaries = inputs.diaries,
            steps = steps,
            readingLogs = inputs.readingLogs
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ScoreTrend(points = emptyList(), thisWeekAverage = 0, lastWeekAverage = 0)
    )

    /**
     * 오늘 무엇을 했는지. 점수와 이름까지 한 묶음입니다.
     *
     * 홈 카드의 점수도, 오늘의 코치가 보는 하루도 여기서 나옵니다. 자리마다 따로 셈하면
     * 카드에 적힌 것과 코치가 보고 말하는 것이 어긋납니다.
     */
    val todayDigest = combine(
        combine(historyEvents, routineChecks, todos, diaries, bookReadingLogs) { events, checks, todoList, diaryList, logs ->
            DayInputs(events, checks, todoList, diaryList, logs)
        },
        moodLogs,
        container.settingsStore.dailyStepsFlow
    ) { inputs, logs, steps ->
        DayScoreCalculator.digest(
            date = LocalDate.now(),
            events = inputs.events,
            routineChecks = inputs.routineChecks,
            todos = inputs.todos,
            diaries = inputs.diaries,
            moodLogs = logs,
            steps = steps,
            readingLogs = inputs.readingLogs
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DayDigest(
            score = DayScore(
                date = LocalDate.now(),
                steps = DailyStep.DEFAULTS,
                doneSteps = emptySet(),
                routineChecks = 0,
                todosDone = 0,
                diaries = 0,
                quotesRead = 0
            ),
            routineTitles = emptyList(),
            todoTitles = emptyList(),
            diaryTitles = emptyList(),
            quoteTitles = emptyList()
        )
    )

    /** 오늘 점수. 홈 카드가 씁니다. 흐름의 마지막 점과 같은 규칙으로 셈합니다. */
    val todayScore = todayDigest
        .map { it.score }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), todayDigest.value.score)

    /** 오늘 받아 둔 한마디. 날이 바뀌면 빈 상태입니다. */
    val dailyCoach = container.settingsStore.dailyCoachStateFlow
        .map { it.forDate(LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyCoachState())

    /**
     * 지금 오늘 기록의 지문. 이것이 바뀌면 홈이 코치에게 다시 물어볼 때인지 들여다봅니다.
     *
     * 꺼 두었으면 빈 값입니다 — 켜지 않은 사람의 화면에서 이 값이 바뀔 이유가 없습니다.
     * 실제로 물을지는 [DailyCoachState.needsAsk]가 정합니다. 여기서는 "들여다볼 때"만 압니다.
     */
    val coachFingerprint = combine(
        todayDigest,
        dailyLoop,
        diaries,
        aiFeedbackSettings
    ) { digest, progress, diaryList, ai ->
        if (!ai.coachEnabled) {
            ""
        } else {
            DailyCoachPrompt.fingerprint(
                today = LocalDate.now(),
                dayDigest = digest,
                progress = progress,
                diaries = diaryList,
                includeDiaryBody = ai.includeDiaryBody
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /**
     * 아직 안 밟은 루틴 한 줄.
     *
     * **AI를 부르지 않습니다.** 이미 있는 기록에서 세는 것이라 값이 들지 않고, 연결이 없어도
     * 뜹니다. 꺼 두었으면 null입니다.
     */
    val routineNudge = combine(
        routines,
        routineChecks,
        aiFeedbackSettings
    ) { routineList, checks, ai ->
        if (!ai.coachNudgeEnabled) {
            null
        } else {
            RoutineNudgeCalculator.build(routineList, checks, LocalDate.now())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _coachLoading = MutableStateFlow(false)

    /** 코치에게 물어보는 중인지. 홈 카드가 기다리는 표시를 띄웁니다. */
    val coachLoading: StateFlow<Boolean> = _coachLoading.asStateFlow()

    private val _coachReplying = MutableStateFlow(false)

    /** 내 말에 코치의 답을 받는 중인지. 대화 화면이 "답을 쓰는 중"을 띄우고 보내기를 잠급니다. */
    val coachReplying: StateFlow<Boolean> = _coachReplying.asStateFlow()

    /** 펼쳐 보고 있는 하루. null이면 닫힌 상태입니다. */
    private val _openedDay = MutableStateFlow<LocalDate?>(null)

    /**
     * 펼쳐 본 하루의 요약.
     *
     * 날짜가 바뀌거나 그날 기록이 바뀌면 다시 셈합니다. 화면에서 셈하지 않고 여기서 만들어
     * 내려보내야 [DayScoreCalculator]를 기기 없이 시험할 수 있습니다.
     */
    val openedDayDigest = combine(
        _openedDay,
        combine(historyEvents, routineChecks, todos, diaries, bookReadingLogs) { events, checks, todoList, diaryList, logs ->
            DayInputs(events, checks, todoList, diaryList, logs)
        },
        moodLogs,
        container.settingsStore.dailyStepsFlow
    ) { date, inputs, logs, steps ->
        date?.let {
            DayScoreCalculator.digest(
                date = it,
                events = inputs.events,
                routineChecks = inputs.routineChecks,
                todos = inputs.todos,
                diaries = inputs.diaries,
                moodLogs = logs,
                steps = steps,
                readingLogs = inputs.readingLogs
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun openDay(date: LocalDate) {
        // 오지 않은 날에 점수를 매길 수는 없습니다.
        _openedDay.value = minOf(date, LocalDate.now())
    }

    fun closeDay() {
        _openedDay.value = null
    }

    /**
     * 홈에 띄울 짚어 주는 문구.
     *
     * 통계 카드가 숫자를 맡고, 이쪽이 그 숫자가 무슨 뜻인지를 맡습니다.
     * 화면에서 셈하지 않고 여기서 만들어 내려보내야 [FeedbackWriter]를 기기 없이 시험할 수 있습니다.
     */
    val feedbackNotes = combine(stats, dailyLoop, routines, routineChecks) { summary, progress, routineList, checks ->
        FeedbackWriter.write(
            summary = summary,
            progress = progress,
            routines = routineList,
            routineChecks = checks,
            today = LocalDate.now()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 지난 이맘때 남긴 것. 없는 날이 대부분이라 대개 빈 목록입니다. */
    val onThisDay = combine(diaries, allItems) { diaryList, items ->
        OnThisDay.find(today = LocalDate.now(), diaries = diaryList, items = items)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 기분별 실천. 기분 그래프와 실천 통계를 겹쳐 본 것입니다. */
    val moodPractice = combine(
        diaries,
        routineChecks,
        historyEvents,
        todos,
        moodLogs
    ) { diaryList, checks, events, todoList, logs ->
        MoodPractice.build(
            diaries = diaryList,
            routineChecks = checks,
            events = events,
            todos = todoList,
            moodLogs = logs
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 오늘 기분을 남깁니다. 하루에 하나라, 다시 찍으면 그날 것을 고칩니다.
     *
     * 일기를 안 쓴 날에도 남길 수 있는 것이 요점입니다. 바쁘고 힘든 날일수록 일기를 못 쓰는데,
     * 그런 날이 통째로 비면 기분 그래프도 겹쳐 보기도 정작 알고 싶은 날을 버립니다.
     */
    fun saveTodayMood(mood: DiaryMood) {
        viewModelScope.launch { container.repository.saveMoodLog(LocalDate.now(), mood) }
    }

    /** 잘못 찍었을 때. 지운 표식을 남겨야 다음 병합에서 되살아나지 않습니다. */
    fun clearTodayMood() {
        viewModelScope.launch { container.repository.clearMoodLog(LocalDate.now()) }
    }

    /** 여기저기 붙여 둔 첨부를 한자리에. 파일을 옮기지 않고 주소만 모읍니다. */
    val attachmentShots = combine(diaries, allItems) { diaryList, items ->
        AttachmentGallery.collect(diaries = diaryList, items = items)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories = allItems
        .map { items ->
            items.map { it.category.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings = container.settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderSettings())

    val serverSyncSettings = container.settingsStore.serverSyncSettingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServerSyncSettings())

    val syncStatus = container.settingsStore.syncStatusFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncStatus())

    /**
     * 앱 업데이트를 확인하고 받는 동안의 상태.
     *
     * 저장하지 않습니다. 앱을 껐다 켜면 처음부터 다시 묻습니다 — 그 사이에 서버에 새 버전이
     * 올라왔을 수 있고, 받아 둔 파일은 캐시라 시스템이 비웠을 수도 있습니다.
     */
    private val _appUpdateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val appUpdateState: StateFlow<AppUpdateState> = _appUpdateState.asStateFlow()

    /**
     * 서버에 새 버전이 있는지 묻습니다.
     *
     * 사용자가 눌러야만 묻습니다. 앱을 켤 때마다 묻는 길은 두지 않았습니다 — 서버가 꺼져 있거나
     * 밖에 있을 때 켤 때마다 실패 문구를 보게 됩니다.
     */
    fun checkForAppUpdate(currentVersionCode: Int) {
        if (_appUpdateState.value is AppUpdateState.Checking) return
        viewModelScope.launch {
            _appUpdateState.value = AppUpdateState.Checking
            val serverUrl = container.settingsStore.getServerSyncSettings().serverUrl
            _appUpdateState.value = runCatching {
                require(serverUrl.isNotBlank()) { "서버 주소를 먼저 넣어 주세요." }
                AppUpdateJson.decide(currentVersionCode, container.appUpdateClient.fetchLatest(serverUrl))
            }.getOrElse { error ->
                AppUpdateState.Failed(error.message ?: "업데이트를 확인하지 못했습니다.")
            }
        }
    }

    /**
     * 설치 파일을 받아 둡니다. 해시가 맞아야 [AppUpdateState.Ready]가 됩니다.
     *
     * 설치는 여기서 하지 않습니다. 안드로이드 설치 화면을 띄우는 일이라 화면 쪽이 맡습니다.
     */
    fun downloadAppUpdate() {
        val info = (_appUpdateState.value as? AppUpdateState.Available)?.info ?: return
        viewModelScope.launch {
            _appUpdateState.value = AppUpdateState.Downloading(info, 0L)
            val serverUrl = container.settingsStore.getServerSyncSettings().serverUrl
            _appUpdateState.value = runCatching {
                val target = AppUpdateInstaller.targetFile(container.appContext, info)
                val file = container.appUpdateClient.download(serverUrl, info, target) { received ->
                    _appUpdateState.value = AppUpdateState.Downloading(info, received)
                }
                AppUpdateState.Ready(info, file.absolutePath)
            }.getOrElse { error ->
                AppUpdateState.Failed(error.message ?: "설치 파일을 받지 못했습니다.")
            }
        }
    }

    /** 실패했거나 다 끝난 뒤 처음으로 되돌립니다. */
    fun clearAppUpdateState() {
        _appUpdateState.value = AppUpdateState.Idle
    }

    private val _sharedText = MutableStateFlow<String?>(null)
    val sharedText: StateFlow<String?> = _sharedText.asStateFlow()

    private val _openItemRequest = MutableStateFlow<Long?>(null)
    val openItemRequest: StateFlow<Long?> = _openItemRequest.asStateFlow()

    /**
     * 위젯의 적기 버튼으로 들어왔는지.
     *
     * 앱을 열자마자 담는 칸이 떠야 합니다. 열어 놓고 사용자가 다시 찾아 들어가면
     * 폰 홈에서 한 번에 담으려던 뜻이 없어집니다.
     */
    private val _captureIdeaRequest = MutableStateFlow(false)
    val captureIdeaRequest: StateFlow<Boolean> = _captureIdeaRequest.asStateFlow()

    fun handleCaptureIdeaRequest() {
        _captureIdeaRequest.value = true
    }

    fun consumeCaptureIdeaRequest() {
        _captureIdeaRequest.value = false
    }

    /**
     * 오늘의 한마디 알림을 눌러 들어왔는지.
     *
     * 홈의 한마디 칸까지 데려갑니다. 앱만 열면 보던 탭이 뜨거나, 홈이어도 글귀 아래 "오늘" 칸이 보여
     * 무엇을 보러 왔는지 다시 찾아야 합니다.
     */
    private val _openDailyCoachRequest = MutableStateFlow(false)
    val openDailyCoachRequest: StateFlow<Boolean> = _openDailyCoachRequest.asStateFlow()

    fun handleOpenDailyCoachRequest() {
        _openDailyCoachRequest.value = true
    }

    fun consumeOpenDailyCoachRequest() {
        _openDailyCoachRequest.value = false
    }

    /** 코치의 답 알림을 눌러 들어왔는지. 그 대화 화면까지 데려갑니다 — 답을 읽고 바로 이어 말할 수 있게. */
    private val _openDailyCoachTalkRequest = MutableStateFlow(false)
    val openDailyCoachTalkRequest: StateFlow<Boolean> = _openDailyCoachTalkRequest.asStateFlow()

    fun handleOpenDailyCoachTalkRequest() {
        _openDailyCoachTalkRequest.value = true
    }

    fun consumeOpenDailyCoachTalkRequest() {
        _openDailyCoachTalkRequest.value = false
    }

    private val _confirmedTodayIds = MutableStateFlow<Set<Long>>(emptySet())
    val confirmedTodayIds: StateFlow<Set<Long>> = _confirmedTodayIds.asStateFlow()

    private val _syncBackups = MutableStateFlow<List<SyncBackup>>(emptyList())
    val syncBackups: StateFlow<List<SyncBackup>> = _syncBackups.asStateFlow()

    private val _syncBackupDirectory = MutableStateFlow("")
    val syncBackupDirectory: StateFlow<String> = _syncBackupDirectory.asStateFlow()

    /**
     * 홈 목록을 섞는 씨앗. 앱을 켤 때마다 새로 정해집니다.
     *
     * 보는 동안에는 그대로여서 목록이 발밑에서 움직이지 않습니다. 잠깐 다른 앱에 다녀오는 것과
     * 다시 켜는 것은 [RESHUFFLE_AFTER_MS]로 가릅니다. 사진을 고르러 나갔다 돌아왔을 뿐인데
     * 읽던 자리가 사라지면 곤란하기 때문입니다.
     */
    private val _shuffleSeed = MutableStateFlow(newShuffleSeed())
    val shuffleSeed: StateFlow<Long> = _shuffleSeed.asStateFlow()

    /** 앱이 화면에서 물러난 시각. 아직 한 번도 물러난 적이 없으면 0입니다. */
    private var leftAtMillis = 0L

    /** 앱이 화면에서 물러난 횟수. 오늘의 한마디를 묻던 중에 앱을 나갔는지 가립니다([refreshDailyCoach]). */
    private var screenLeaves = 0

    val appLockEnabled = container.settingsStore.appLockEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** 일기만 따로 거는 잠금. 앱 잠금과 별개입니다. */
    val diaryLockEnabled = container.settingsStore.diaryLockEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /**
     * 잠금의 세 가지 상태.
     *
     * 세 번째([AppLockState.CHECKING])가 필요한 이유가 있습니다. 설정은 DataStore에서 읽어 오는데
     * 그동안 화면은 이미 그려집니다. "잠김/열림" 둘뿐이면 어느 쪽을 처음 값으로 두든 한쪽이
     * 손해입니다. 열림으로 두면 잠금을 켠 사람에게 첫 한 순간 내용이 비치고, 잠김으로 두면
     * 켜지 않은 대다수가 열 때마다 잠금 화면이 깜빡입니다. 읽는 동안에는 아무것도 그리지 않습니다.
     */
    private val _lockState = MutableStateFlow(AppLockState.CHECKING)
    val lockState: StateFlow<AppLockState> = _lockState.asStateFlow()

    fun unlock() {
        _lockState.value = AppLockState.OPEN
    }

    private val routineDayRange = MutableStateFlow(container.repository.todayRangeMillis())
    val routineTodayCounts = combine(routineChecks, routineDayRange) { checks, range ->
        val (start, end) = range
        checks
            .filter { it.checkedAt in start..end }
            .groupingBy { it.routineId }
            .eachCount()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        viewModelScope.launch {
            container.repository.seedDefaultsIfNeeded()
            _confirmedTodayIds.value = container.repository.getTodayConfirmedIds()
            _syncBackupDirectory.value = container.syncBackupStore.directoryPath()
            reloadSyncBackups()
            // 앱을 다시 깔거나 기기를 껐다 켜면 예약이 사라질 수 있어 시작할 때 맞춰 둔다.
            container.reminderScheduler.syncAutoSync(container.settingsStore.getServerSyncSettings())
            val aiSettings = container.settingsStore.getAiFeedbackSettings()
            container.reminderScheduler.syncGrowthFeedback(aiSettings)
            container.reminderScheduler.syncGrowthNudge(aiSettings)
            container.reminderScheduler.syncDailyCoach(aiSettings, container.dailyCoachCoordinator.state())
            _lockState.value = if (container.settingsStore.appLockEnabledFlow.first()) {
                AppLockState.LOCKED
            } else {
                AppLockState.OPEN
            }
        }

        // 홈 맨 위의 글귀. 앱을 열 때마다 새 글귀로 넘깁니다(설정에서 끌 수 있습니다 — 끄면 위젯·알림이 마지막으로
        // 보여 준 것이 그대로 남습니다). 가리키던 글귀를 지웠거나(동기화로 사라진 것도) 아직 고른 것이 없어도 하나 고릅니다.
        viewModelScope.launch {
            var opening = true
            homeQuote.filterNotNull().collect { quote ->
                val newOnOpen = opening && container.settingsStore.getSettings().showOnLaunch
                opening = false
                if (newOnOpen || (quote.item == null && quote.hasCandidates)) {
                    pickHomeQuote(ExposureTrigger.APP_LAUNCH)
                }
            }
        }

        // 배경 동기화는 화면 밖에서 데이터를 바꿉니다.
        // 목록은 Room이 흘려 주지만 백업 파일과 오늘 읽음 표시는 여기서 직접 읽으므로,
        // 동기화 결과가 기록될 때 함께 다시 읽지 않으면 앱을 껐다 켤 때까지 옛 값이 남습니다.
        viewModelScope.launch {
            container.settingsStore.syncStatusFlow
                .distinctUntilChanged()
                // 첫 값은 시작할 때 이미 읽었습니다.
                .drop(1)
                .collect {
                    reloadSyncBackups()
                    _confirmedTodayIds.value = container.repository.getTodayConfirmedIds()
                }
        }
    }

    fun handleSharedText(text: String?) {
        _sharedText.value = text?.trim()?.takeIf { it.isNotBlank() }
    }

    fun clearSharedText() {
        _sharedText.value = null
    }

    fun handleOpenItemRequest(
        itemId: Long,
        markConfirmed: Boolean,
        recordView: Boolean
    ) {
        viewModelScope.launch {
            if (recordView) {
                container.repository.recordContentViewed(
                    contentItemId = itemId,
                    trigger = ExposureTrigger.NOTIFICATION_TAP
                )
            }
            if (markConfirmed) {
                val confirmed = container.repository.markContentConfirmed(
                    contentItemId = itemId,
                    trigger = ExposureTrigger.NOTIFICATION_TAP
                )
                if (confirmed) {
                    _confirmedTodayIds.value = _confirmedTodayIds.value + itemId
                }
            }
            _openItemRequest.value = itemId
        }
    }

    fun consumeOpenItemRequest() {
        _openItemRequest.value = null
    }

    fun refreshConfirmedToday() {
        viewModelScope.launch {
            _confirmedTodayIds.value = container.repository.getTodayConfirmedIds()
        }
    }

    fun refreshRoutineToday() {
        routineDayRange.value = container.repository.todayRangeMillis()
    }

    /** 지금 바로 다시 섞습니다. 홈의 섞기 버튼이 씁니다. */
    fun reshuffleContent() {
        _shuffleSeed.value = newShuffleSeed()
    }

    fun onAppBackgrounded() {
        leftAtMillis = SystemClock.elapsedRealtime()
        screenLeaves += 1
        // 화면에 있는 동안 홈이 물었을 수 있습니다. 배경 한마디 알람을 마지막으로 물은 때에 맞춰 다시 겁니다.
        viewModelScope.launch {
            container.reminderScheduler.syncDailyCoach(
                container.settingsStore.getAiFeedbackSettings(),
                container.dailyCoachCoordinator.state()
            )
        }
    }

    /**
     * 잠금을 켜고 끕니다.
     *
     * 끌 때는 바로 풀어 줍니다. 껐는데 잠금 화면이 남아 있으면 사용자가 갇힙니다.
     */
    fun setDiaryLockEnabled(enabled: Boolean) {
        viewModelScope.launch { container.settingsStore.setDiaryLockEnabled(enabled) }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsStore.setAppLockEnabled(enabled)
            if (!enabled) _lockState.value = AppLockState.OPEN
        }
    }

    /**
     * 앱이 화면 앞으로 돌아왔습니다. 오래 비웠으면 "다시 켠 것"으로 보고 새로 섞습니다.
     *
     * 화면을 돌릴 때도 여기를 지나지만, 나갔다 온 시간이 0에 가까워 섞이지 않습니다.
     */
    fun onAppForegrounded() {
        val awayMillis = SystemClock.elapsedRealtime() - leftAtMillis
        if (leftAtMillis != 0L && awayMillis >= RESHUFFLE_AFTER_MS) {
            reshuffleContent()
        }
        // 잠깐 사진을 고르러 나갔다 온 것까지 잠그면 성가십니다. 오래 비웠을 때만 다시 잠급니다.
        if (leftAtMillis != 0L && awayMillis >= LOCK_AFTER_MS && appLockEnabled.value) {
            _lockState.value = AppLockState.LOCKED
        }
        leftAtMillis = 0L
    }

    /** 0은 "섞지 않음"이라 씨앗으로 쓰지 않습니다([ContentShuffle]). */
    private fun newShuffleSeed(): Long {
        return generateSequence { Random.nextLong() }.first { it != 0L }
    }

    /** 홈의 "다음 글귀". 위젯과 같은 자리를 바꾸므로 위젯도 함께 넘어갑니다. */
    fun nextHomeQuote() {
        viewModelScope.launch { pickHomeQuote(ExposureTrigger.MANUAL_REFRESH) }
    }

    private suspend fun pickHomeQuote(trigger: ExposureTrigger) {
        QuoteWidget.pickNextItem(container, trigger) ?: return
        QuoteWidget().updateAll(container.appContext)
    }

    fun recordContentViewed(
        itemId: Long,
        trigger: ExposureTrigger = ExposureTrigger.DETAIL_OPEN
    ) {
        viewModelScope.launch {
            container.repository.recordContentViewed(
                contentItemId = itemId,
                trigger = trigger
            )
        }
    }

    fun toggleFavorite(itemId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            container.repository.setFavorite(itemId, isFavorite)
        }
    }

    fun updateSettings(updated: ReminderSettings) {
        viewModelScope.launch {
            val normalized = updated.copy(intervalMinutes = updated.effectiveIntervalMinutes)
            container.settingsStore.updateSettings(normalized)
            container.reminderScheduler.sync(normalized)
        }
    }

    fun updateServerSyncSettings(updated: ServerSyncSettings) {
        viewModelScope.launch {
            container.settingsStore.updateServerSyncSettings(updated)
            container.reminderScheduler.syncAutoSync(updated)
        }
    }

    fun sendTestNotification() {
        viewModelScope.launch {
            val currentSettings = container.settingsStore.getSettings()
            val item = container.repository.getRandomContent(currentSettings.categoryFilter)
            val routine = container.repository.getRandomReminderRoutine()
            val shouldShowRoutine = routine != null && (item == null || Random.nextBoolean())

            if (shouldShowRoutine && routine != null) {
                val todayCount = container.repository.getTodayRoutineCheckCount(routine.id)
                AppNotifications.showRoutineNotification(
                    context = container.appContext,
                    routine = routine,
                    todayCount = todayCount,
                    settings = currentSettings
                )
            } else if (item != null) {
                container.repository.recordContentSurfaced(
                    item = item,
                    trigger = ExposureTrigger.TEST_NOTIFICATION
                )
                AppNotifications.showContentNotification(container.appContext, item, currentSettings)
            }
        }
    }

    /**
     * 기기 데이터를 파일로 내보냅니다.
     *
     * 화면이 들고 있는 목록이 아니라 DB에서 통째로 읽습니다. 화면에 없는 종류(일기·할 일·책 등)를
     * 빠뜨리지 않으려는 것입니다. 예전에는 여기서 종류를 하나씩 넘겨 주다가 셋을 빠뜨렸고,
     * 그 파일로 복원하면 기기의 일기가 사라졌습니다.
     */
    suspend fun exportData(uri: Uri): Result<Int> = runCatching {
        container.appDataExporter.export(uri = uri, snapshot = currentSnapshot())
    }

    suspend fun importData(uri: Uri): Result<AppImportResult> = runCatching {
        val result = container.appDataImporter.import(uri)
        _confirmedTodayIds.value = container.repository.getTodayConfirmedIds()
        result
    }

    /** 서버 없이 다른 기기의 내보내기 파일과 합칩니다. 교체가 아니라 병합입니다. */
    suspend fun mergeFromFile(uri: Uri): Result<ServerSyncResult> = runCatching {
        // 되돌릴 수 있도록 합치기 전 상태를 남긴다.
        val backup = container.syncBackupStore.save(SyncBackupKind.BEFORE_MERGE, currentSnapshot())
        val result = container.appDataImporter.mergeFromFile(uri, currentSnapshot())
        _confirmedTodayIds.value = container.repository.getTodayConfirmedIds()
        reloadSyncBackups()
        ServerSyncResult(counts = result, backup = backup)
    }

    suspend fun testServerConnection(): Result<ServerConnectionInfo> = runCatching {
        container.serverSyncClient.testConnection(container.settingsStore.getServerSyncSettings())
    }

    suspend fun syncWithServer(): Result<ServerSyncResult> = runCatching {
        // 배경 동기화와 같은 절차를 쓴다.
        val result = container.syncCoordinator.merge(SyncBackupKind.BEFORE_MERGE)
        _confirmedTodayIds.value = container.repository.getTodayConfirmedIds()
        reloadSyncBackups()
        result
    }

    suspend fun uploadDataToServer(): Result<ServerSyncResult> = runCatching {
        val syncSettings = container.settingsStore.getServerSyncSettings()
        // 서버 데이터를 통째로 덮어쓰기 전에 현재 서버 상태를 백업한다.
        val serverSnapshotBefore = container.serverSyncClient.downloadSnapshot(syncSettings)
        val backup = container.syncBackupStore.save(SyncBackupKind.BEFORE_UPLOAD, serverSnapshotBefore)
        val serverSnapshot = container.serverSyncClient.uploadSnapshot(
            settings = syncSettings,
            snapshot = currentSnapshot()
        )
        reloadSyncBackups()
        ServerSyncResult(
            counts = AppImportResult(
                itemCount = serverSnapshot.items.size,
                eventCount = serverSnapshot.events.size,
                routineCount = serverSnapshot.routines.size,
                routineCheckCount = serverSnapshot.routineChecks.size,
                routineMemoCount = serverSnapshot.routineMemos.size,
                contentMemoCount = serverSnapshot.contentMemos.size
            ),
            backup = backup
        )
    }

    suspend fun downloadDataFromServer(): Result<ServerSyncResult> = runCatching {
        val syncSettings = container.settingsStore.getServerSyncSettings()
        // 내려받기에 실패하면 기기 데이터를 건드리지 않도록 스냅샷을 먼저 받는다.
        val snapshot = container.serverSyncClient.downloadSnapshot(syncSettings)
        val backup = container.syncBackupStore.save(SyncBackupKind.BEFORE_DOWNLOAD, currentSnapshot())
        val result = container.appDataImporter.importSnapshot(snapshot)
        _confirmedTodayIds.value = container.repository.getTodayConfirmedIds()
        reloadSyncBackups()
        ServerSyncResult(counts = result, backup = backup)
    }

    suspend fun restoreSyncBackup(backup: SyncBackup): Result<AppImportResult> = runCatching {
        val snapshot = container.syncBackupStore.load(backup)
        container.syncBackupStore.save(SyncBackupKind.BEFORE_RESTORE, currentSnapshot())
        val result = container.appDataImporter.importSnapshot(snapshot)
        _confirmedTodayIds.value = container.repository.getTodayConfirmedIds()
        reloadSyncBackups()
        result
    }

    // ---- 일기 ----

    suspend fun getDiary(id: Long): DiaryEntity? = container.repository.getDiaryById(id)

    suspend fun saveDiary(draft: DiaryDraft): Result<Unit> = runCatching {
        container.repository.saveDiary(draft)
        Unit
    }

    suspend fun deleteDiary(id: Long): Result<Unit> = runCatching {
        container.repository.deleteDiary(id)
    }

    // ---- 독서 ----

    suspend fun saveBook(draft: BookDraft): Result<Unit> = runCatching {
        container.repository.saveBook(draft)
        Unit
    }

    suspend fun updateBookProgress(id: Long, currentPage: Int): Result<Unit> = runCatching {
        container.repository.updateBookProgress(id, currentPage)
        Unit
    }

    suspend fun toggleBookFinished(id: Long): Result<Unit> = runCatching {
        container.repository.toggleBookFinished(id)
        Unit
    }

    suspend fun deleteBook(id: Long): Result<Unit> = runCatching {
        container.repository.deleteBook(id)
    }

    /** 독후감만 저장합니다. 진도·상태는 건드리지 않습니다. */
    suspend fun saveBookReview(bookId: Long, review: String): Result<Unit> = runCatching {
        container.repository.saveBookReview(bookId, review)
        Unit
    }

    suspend fun extractQuoteFromBook(bookId: Long, body: String, page: Int): Result<Unit> = runCatching {
        container.repository.extractQuoteFromBook(bookId, body, page)
        Unit
    }

    // ---- 할 일 ----

    suspend fun getTodo(id: Long): TodoEntity? = container.repository.getTodoById(id)

    /** 저장한 뒤 알람을 다시 겁니다. 시각을 바꿨는데 예약이 그대로면 옛 시각에 울립니다. */
    suspend fun saveTodo(draft: TodoDraft): Result<Unit> = runCatching {
        val saved = container.repository.saveTodo(draft)
        container.todoAlarmScheduler.sync(saved)
    }

    suspend fun toggleTodoDone(id: Long): Result<Unit> = runCatching {
        // 끝낸 일의 알람이 그대로 울리면 이미 한 일을 다시 하라고 하는 셈이다.
        container.repository.toggleTodoDone(id)?.let(container.todoAlarmScheduler::sync)
        Unit
    }

    suspend fun deleteTodo(id: Long): Result<Unit> = runCatching {
        container.repository.getTodoById(id)?.let(container.todoAlarmScheduler::cancel)
        container.repository.deleteTodo(id)
    }

    /** 정확한 알람 권한이 없으면 알람이 늦게 울릴 수 있어 화면에서 알려 줘야 합니다. */
    fun canScheduleExactAlarms(): Boolean = container.todoAlarmScheduler.canScheduleExact()

    fun exactAlarmSettingsIntent() = container.todoAlarmScheduler.exactAlarmSettingsIntent()

    private suspend fun reloadSyncBackups() {
        _syncBackups.value = container.syncBackupStore.list()
    }

    suspend fun resetTodayConfirmed(): Result<Int> = runCatching {
        val removedCount = container.repository.clearTodayConfirmed()
        _confirmedTodayIds.value = emptySet()
        removedCount
    }

    suspend fun resetViewCounts(): Result<Int> = runCatching {
        container.repository.resetViewCounts()
    }

    suspend fun saveRoutine(draft: RoutineDraft): Result<Unit> = runCatching {
        val normalized = draft.copy(
            title = draft.title.trim(),
            note = draft.note.trim(),
            category = draft.category.trim()
        )
        require(normalized.title.isNotBlank()) { "루틴 이름을 입력해 주세요." }
        container.repository.saveRoutine(normalized)
    }

    suspend fun deleteRoutine(routineId: Long): Result<Unit> = runCatching {
        container.repository.deleteRoutine(routineId)
    }

    /** 루틴을 한 칸 위/아래로 옮깁니다. 이미 끝이라 옮기지 못했으면 false. */
    suspend fun moveRoutine(routineId: Long, up: Boolean): Result<Boolean> = runCatching {
        container.repository.moveRoutine(routineId, up)
    }

    /**
     * AI에게 성장 피드백을 받아 저장합니다.
     *
     * 실패 사유는 사용자가 읽고 무엇을 고칠지 알 수 있는 말이어야 합니다. 열쇠가 없는 것과
     * 인터넷이 끊긴 것은 할 일이 다릅니다.
     */
    suspend fun requestGrowthFeedback(period: ReportPeriod): Result<GrowthReportEntity> = runCatching {
        container.growthFeedbackCoordinator.generate(period)
    }

    /** 보내기 전에 무엇이 나가는지 보여 줍니다. 실제로 보내는 글과 같은 함수로 만듭니다. */
    suspend fun previewGrowthPrompt(period: ReportPeriod): Result<String> = runCatching {
        container.growthFeedbackCoordinator.preview(period)
    }

    /**
     * 물음을 복사해 갔다고 적어 둡니다.
     *
     * AI에 못 붙는 자리를 위한 길입니다. 복사해 채팅창에 붙여넣고 받아 온 답을
     * [saveGrowthAnswer]로 다시 앱에 남깁니다. 그 사이 앱이 꺼져도 구간을 잃지 않게
     * 화면이 아니라 설정에 적습니다.
     */
    suspend fun markGrowthPromptCopied(period: ReportPeriod): Result<Unit> = runCatching {
        container.growthFeedbackCoordinator.markPromptCopied(period)
    }

    /** 채팅창에서 받아 온 답을 그대로 한 편으로 남깁니다. */
    suspend fun saveGrowthAnswer(rawText: String): Result<GrowthReportEntity> = runCatching {
        container.growthFeedbackCoordinator.saveManualAnswer(rawText)
    }

    suspend fun deleteGrowthReport(reportId: Long): Result<Unit> = runCatching {
        container.repository.deleteGrowthReport(reportId)
    }

    /** 오래된 것을 한 번에 치웁니다. 지운 편 수를 돌려줍니다. */
    suspend fun deleteGrowthReports(reportIds: List<Long>): Result<Int> = runCatching {
        container.repository.deleteGrowthReports(reportIds)
    }

    /**
     * 오늘의 한마디를 받아 옵니다.
     *
     * 홈이 열릴 때마다 불러도 됩니다. 실제로 물을 때인지는 [DailyCoachCoordinator]가 가립니다 —
     * 오늘 한 것이 있고, 주기가 돌아왔고, 하루 상한에 닿지 않았을 때만 나갑니다. 앱을 닫아 둔 동안에는
     * 같은 규칙으로 배경 일꾼이 묻습니다([com.codex.appgoodwords.work.DailyCoachWorker]).
     *
     * @param force 사용자가 "다시 받기"를 눌렀을 때. 그때는 같은 기록이어도 묻습니다.
     */
    fun refreshDailyCoach(force: Boolean = false) {
        viewModelScope.launch {
            // 화면이 다시 그려지거나 손가락이 두 번 닿아도 한 번만 나갑니다.
            if (_coachLoading.value) return@launch
            _coachLoading.value = true
            // 묻는 데 몇 분이 걸리기도 합니다. 그 사이 앱을 나가 끊긴 것은 세지 않고 배경 일꾼에게 넘깁니다.
            val leavesAtStart = screenLeaves
            val startedAt = System.currentTimeMillis()
            try {
                val tip = container.dailyCoachCoordinator.refresh(
                    force = force,
                    now = startedAt,
                    interrupted = { screenLeaves != leavesAtStart }
                ).tip
                // 끊기지 않고 앱 밖에서 답이 왔으면 알립니다. 화면에 없는 카드는 알릴 수 없고, 묻던 것을 두고
                // 나간 사람은 다시 열어 보기 전까지 온 줄 모릅니다. 배경 일꾼이 알리는 것과 같습니다.
                if (tip != null && tip.createdAt >= startedAt && leftAtMillis != 0L) {
                    val reminderSettings = container.settingsStore.getSettings()
                    AppNotifications.showDailyCoachNotification(
                        context = container.appContext,
                        tip = tip,
                        soundEnabled = reminderSettings.notificationSoundEnabled,
                        lockScreenVisible = reminderSettings.lockScreenVisible
                    )
                }
            } finally {
                _coachLoading.value = false
            }
        }
    }

    /** 새로 온 한마디를 보았다고 적습니다. 카드의 반짝임과 홈 탭의 점을 거둡니다. */
    fun markDailyCoachSeen() {
        viewModelScope.launch { container.dailyCoachCoordinator.markSeen() }
    }

    /** 권해 받은 글귀를 보관함에 담습니다. 담은 것은 다시 담지 않습니다. */
    suspend fun keepCoachQuote(): Result<Unit> = runCatching {
        container.dailyCoachCoordinator.keepQuote()
    }

    suspend fun keepCoachRoutine(): Result<Unit> = runCatching {
        container.dailyCoachCoordinator.keepRoutine()
    }

    /** 해 볼 한 가지를 오늘 할 일로 담습니다. */
    suspend fun keepCoachAction(): Result<Unit> = runCatching {
        container.dailyCoachCoordinator.keepAction()
    }

    /** 코치의 답에 딸린 해 볼 일을 오늘 할 일로 담습니다. [at]은 그 답이 온 때입니다. */
    suspend fun keepCoachAnswerAction(at: Long): Result<Unit> = runCatching {
        container.dailyCoachCoordinator.keepAnswerAction(at)
    }

    /**
     * 받은 한마디에 내가 한 말을 보내고 코치의 답을 받습니다(사용자가 정함, 2026-10-29).
     *
     * 보낸 말은 곧바로 적히고, 답은 받는 대로 대화에 붙습니다([com.codex.appgoodwords.data.DailyCoachCoordinator.reply]).
     */
    fun replyToCoach(text: String) {
        talkToCoach { interrupted -> container.dailyCoachCoordinator.reply(text, interrupted = interrupted) }
    }

    /** 답을 받지 못한 마지막 말에 다시 답을 받습니다 — "다시 받기", 그리고 끊긴 것을 앱으로 돌아와 이어 받을 때. */
    fun retryCoachReply() {
        talkToCoach { interrupted -> container.dailyCoachCoordinator.retryReply(interrupted = interrupted) }
    }

    /** 답을 기다리는 말의 수. 모두 답을 받아야 "답을 쓰는 중"을 거둡니다. */
    private var talksInFlight = 0

    /**
     * 답을 받는 길은 한마디와 같습니다([refreshDailyCoach]) — 그 사이 앱을 나가 끊긴 것은 세지 않고 배경 일꾼에게 넘기고,
     * 끊기지 않고 앱 밖에서 답이 왔으면 알립니다.
     *
     * 답을 기다리는 동안 대화 화면은 보내기를 잠그지만, 여기서 **보낸 말을 버리지는 않습니다.** 차례는 코디네이터가 하나씩
     * 지키므로([com.codex.appgoodwords.data.DailyCoachCoordinator.reply]) 겹쳐 와도 모두 답을 받습니다.
     */
    private fun talkToCoach(ask: suspend (interrupted: () -> Boolean) -> DailyCoachState) {
        viewModelScope.launch {
            talksInFlight += 1
            _coachReplying.value = true
            val leavesAtStart = screenLeaves
            val startedAt = System.currentTimeMillis()
            try {
                val state = ask { screenLeaves != leavesAtStart }
                val answer = state.answerSince(startedAt)
                if (answer != null && leftAtMillis != 0L) {
                    val reminderSettings = container.settingsStore.getSettings()
                    AppNotifications.showDailyCoachAnswerNotification(
                        context = container.appContext,
                        reply = state.replyBefore(answer),
                        answer = answer,
                        soundEnabled = reminderSettings.notificationSoundEnabled,
                        lockScreenVisible = reminderSettings.lockScreenVisible
                    )
                }
            } finally {
                talksInFlight -= 1
                _coachReplying.value = talksInFlight > 0
            }
        }
    }

    /** 코치에게 무엇이 나가는지 보여 줍니다. 실제로 보내는 글과 같은 함수로 만듭니다. */
    suspend fun previewDailyCoachPrompt(): Result<String> = runCatching {
        container.dailyCoachCoordinator.preview()
    }

    fun updateAiFeedbackSettings(updated: AiFeedbackSettings) {
        viewModelScope.launch {
            container.settingsStore.updateAiFeedbackSettings(updated)
            container.reminderScheduler.syncGrowthFeedback(updated)
            container.reminderScheduler.syncGrowthNudge(updated)
            container.reminderScheduler.syncDailyCoach(updated, container.dailyCoachCoordinator.state())
        }
    }

    /**
     * 번뜩인 것을 한 줄로 담습니다.
     *
     * 담는 화면까지 들어가면 여섯 걸음이라 그 사이에 날아갑니다. 제목만 받고 나머지는
     * 비워 둡니다. 길게 풀 것이 있으면 담긴 뒤에 눌러 들어가 이어 쓰면 됩니다.
     */
    suspend fun captureIdea(title: String): Result<Unit> = runCatching {
        val trimmed = title.trim()
        require(trimmed.isNotBlank()) { "적은 것이 없습니다." }
        container.repository.saveContent(ContentDraft(type = ContentType.IDEA, title = trimmed))
    }

    suspend fun captureInboxText(text: String): Result<Boolean> = runCatching {
        val draft = com.codex.appgoodwords.data.SharedText.toDraft(text)
            ?: error("붙여넣을 텍스트가 없습니다.")
        container.repository.saveSharedContent(draft).alreadyKept
    }

    suspend fun classifyInboxItem(id: Long, destination: ContentType): Result<Unit> = runCatching {
        container.repository.classifyInboxItem(id, destination)
        Unit
    }

    /**
     * 원문을 AI에게 설명받고, 성공한 경우에만 기록을 바꿉니다.
     *
     * 임시저장 글은 개념으로 옮기고, 이미 개념인 것(직접 적었거나 다시 묻는 것)은
     * 그 자리에서 설명만 새로 받습니다.
     */
    suspend fun explainConcept(id: Long) = runCatching {
        val item = container.repository.getContentItem(id) ?: error("설명할 글을 찾을 수 없습니다.")
        check(item.type == ContentType.INBOX || item.type == ContentType.CONCEPT) {
            "임시저장 글이나 개념만 AI에게 물어볼 수 있습니다."
        }
        val aiSettings = container.settingsStore.getAiFeedbackSettings()
        val rawAnswer = container.aiFeedbackClient.ask(
            system = ConceptExplanation.systemPrompt(),
            user = ConceptExplanation.userPrompt(item),
            model = aiSettings.effectiveModel,
            aiSettings = aiSettings,
            syncSettings = container.settingsStore.getServerSyncSettings()
        )
        val explanation = ConceptExplanation.parse(rawAnswer)
        container.repository.saveConceptExplanation(id, explanation)
    }

    /**
     * 글귀를 오늘부터 밟을 루틴으로 뽑아냅니다.
     *
     * 모아 두는 것과 실천하는 것이 한 앱에 있는데, 그 사이를 잇는 길이 AI 추천에만 있었습니다.
     * 뽑아낸 글귀를 함께 적어 두어, 나중에 루틴 쪽에서도 글귀 쪽에서도 이어진 것이 보입니다.
     */
    suspend fun makeRoutineFromQuote(itemId: Long, title: String, note: String = ""): Result<Unit> =
        runCatching {
            container.repository.extractRoutineFromContent(
                contentItemId = itemId,
                title = title,
                note = note
            )
        }

    /** 글귀를 오늘 할 일로 옮깁니다. 날짜는 할 일 화면에서 바꿉니다. */
    suspend fun makeTodoFromQuote(title: String): Result<Unit> = runCatching {
        val trimmed = title.trim()
        require(trimmed.isNotBlank()) { "할 일 이름이 비어 있습니다." }
        container.repository.saveTodo(TodoDraft(title = trimmed, dueDate = LocalDate.now()))
    }

    /** 추천 글귀를 보관함에 담습니다. 읽고 마는 대신 남겨 두려는 것입니다. */
    suspend fun keepSuggestedQuote(report: GrowthReportEntity): Result<Unit> = runCatching {
        require(report.suggestedQuote.isNotBlank()) { "담을 글귀가 없습니다." }
        container.repository.saveContent(
            ContentDraft(
                type = ContentType.QUOTE,
                title = report.suggestedQuote.take(40),
                body = report.suggestedQuote,
                author = report.suggestedQuoteAuthor,
                category = AI_CATEGORY
            )
        )
    }

    /** 추천 루틴을 하루 끝에 붙입니다. */
    suspend fun keepSuggestedRoutine(title: String): Result<Unit> = runCatching {
        val trimmed = title.trim()
        require(trimmed.isNotBlank()) { "루틴 이름이 비어 있습니다." }
        container.repository.saveRoutine(RoutineDraft(title = trimmed, category = AI_CATEGORY))
    }

    /** 루틴을 [targetIndex](0부터) 자리로 한 번에 옮깁니다. */
    suspend fun moveRoutineTo(routineId: Long, targetIndex: Int): Result<Boolean> = runCatching {
        container.repository.moveRoutineTo(routineId, targetIndex)
    }

    suspend fun checkRoutine(routineId: Long): Result<Int> = runCatching {
        container.repository.markRoutineDone(routineId)
    }

    suspend fun saveRoutineMemo(routineId: Long, body: String): Result<Long> = runCatching {
        container.repository.saveRoutineMemo(routineId, body)
    }

    suspend fun deleteRoutineMemo(memoId: Long): Result<Int> = runCatching {
        container.repository.deleteRoutineMemo(memoId)
    }

    /** 글귀에 메모를 답니다. 루틴 메모와 달리 실천으로 세지 않습니다. */
    suspend fun saveContentMemo(contentItemId: Long, body: String): Result<Long> = runCatching {
        container.repository.saveContentMemo(contentItemId, body)
    }

    suspend fun deleteContentMemo(memoId: Long): Result<Int> = runCatching {
        container.repository.deleteContentMemo(memoId)
    }

    suspend fun deleteHistoryEvents(eventIds: Set<Long>): Result<Int> = runCatching {
        require(eventIds.isNotEmpty()) { "삭제할 이력이 없습니다." }
        val removedCount = container.repository.deleteExposureEvents(eventIds)
        _confirmedTodayIds.value = container.repository.getTodayConfirmedIds()
        removedCount
    }

    suspend fun deleteCategory(category: String): Result<Int> = runCatching {
        val normalized = category.trim()
        require(normalized.isNotBlank()) { "삭제할 카테고리가 없습니다." }

        val affected = container.repository.removeCategory(normalized)
        val currentSettings = container.settingsStore.getSettings()
        if (currentSettings.categoryFilter == normalized) {
            val updated = currentSettings.copy(categoryFilter = "")
            container.settingsStore.updateSettings(updated)
            container.reminderScheduler.sync(updated)
        }
        affected
    }

    suspend fun saveContent(draft: ContentDraft): Result<Unit> = runCatching {
        // 공유로 바로 담는 길(ShareTargetActivity)과 같은 규칙을 씁니다.
        // 여기에 따로 두면 같은 링크가 담는 길에 따라 다른 종류가 됩니다.
        val normalized = ContentNormalizer.normalize(draft)
        ContentNormalizer.validate(normalized)
        container.repository.saveContent(normalized)
    }

    suspend fun deleteContent(itemId: Long): Result<Unit> = runCatching {
        container.repository.deleteContent(itemId)
        _confirmedTodayIds.value = _confirmedTodayIds.value - itemId
    }

    /** 이상향·목표·롤모델을 적거나 고칩니다. 되새긴 기록·이어 둔 루틴·홈에 두기는 그대로 둡니다. */
    suspend fun saveGoal(id: Long, type: ContentType, title: String, body: String): Result<Long> = runCatching {
        container.repository.saveGoal(id, type, title, body)
    }

    /** 이 이상향·목표·롤모델을 위해 하는 루틴을 바꿉니다. */
    suspend fun setGoalRoutines(id: Long, routineSyncIds: List<String>): Result<Unit> = runCatching {
        container.repository.setGoalRoutines(id, routineSyncIds)
    }

    /** 홈 "오늘" 칸 맨 위에 둘지. 보관함의 즐겨찾기와 같은 칸(`isFavorite`)을 씁니다. */
    suspend fun setGoalPinned(id: Long, pinned: Boolean): Result<Unit> = runCatching {
        container.repository.setFavorite(id, pinned)
    }

    suspend fun toggleContentConfirmed(itemId: Long): Result<Boolean> {
        val previousConfirmedIds = _confirmedTodayIds.value
        val optimisticConfirmed = itemId !in previousConfirmedIds
        _confirmedTodayIds.value = applyConfirmedState(
            base = previousConfirmedIds,
            itemId = itemId,
            confirmed = optimisticConfirmed
        )

        val result = runCatching {
            container.repository.toggleContentConfirmed(
                contentItemId = itemId,
                trigger = ExposureTrigger.DETAIL_CHECK
            )
        }

        if (result.isSuccess) {
            _confirmedTodayIds.value = applyConfirmedState(
                base = previousConfirmedIds,
                itemId = itemId,
                confirmed = result.getOrThrow()
            )
        } else {
            _confirmedTodayIds.value = previousConfirmedIds
        }

        return result
    }

    suspend fun fetchLinkMetadata(url: String): Result<LinkMetadata> = runCatching {
        require(url.isNotBlank()) { "링크를 먼저 입력해 주세요." }
        container.repository.fetchLinkMetadata(url.trim())
    }

    private fun applyConfirmedState(
        base: Set<Long>,
        itemId: Long,
        confirmed: Boolean
    ): Set<Long> {
        return if (confirmed) {
            base + itemId
        } else {
            base - itemId
        }
    }

    private suspend fun currentSnapshot(): AppDataSnapshot = container.syncCoordinator.currentSnapshot()

    private companion object {
        /**
         * 이만큼 넘게 앱을 떠나 있었으면 다시 켠 것으로 봅니다.
         *
         * 짧게 잡으면 링크를 열어 보고 돌아올 때마다 읽던 자리가 사라지고,
         * 길게 잡으면 아침에 열어도 어제와 같은 차례가 나옵니다.
         */
        const val RESHUFFLE_AFTER_MS = 10 * 60 * 1000L

        /**
         * 이만큼 넘게 앱을 떠나 있었으면 다시 잠급니다.
         *
         * 짧게 잡으면 사진을 고르러 갤러리에 다녀올 때마다 잠기고, 길게 잡으면 잠가 둔 뜻이 없습니다.
         */
        const val LOCK_AFTER_MS = 60 * 1000L
    }
}

/** 하루 요약에 넣을 기록들. combine이 한 번에 넷까지만 받아서 묶습니다. */
private data class DayInputs(
    val events: List<ExposureEventEntity>,
    val routineChecks: List<RoutineCheckEntity>,
    val todos: List<TodoEntity>,
    val diaries: List<DiaryEntity>,
    val readingLogs: List<BookReadingLogEntity> = emptyList()
)

/** combine이 한 번에 넷까지만 받아서, 통계에 넣을 것들을 한 덩어리로 묶습니다. */
private data class StatsInputs(
    val diaries: List<DiaryEntity>,
    val todos: List<TodoEntity>,
    val books: List<BookEntity>,
    val moodLogs: List<MoodLogEntity>
)
