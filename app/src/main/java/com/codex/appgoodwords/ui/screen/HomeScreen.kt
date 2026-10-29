package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.DailyCoachState
import com.codex.appgoodwords.data.DailyProgress
import com.codex.appgoodwords.data.DailyStep
import com.codex.appgoodwords.data.DayScore
import com.codex.appgoodwords.data.ScoreTrend
import com.codex.appgoodwords.data.FeedbackKind
import com.codex.appgoodwords.data.FeedbackNote
import com.codex.appgoodwords.data.GrowthReportEntity
import com.codex.appgoodwords.data.HomeGoalLine
import com.codex.appgoodwords.data.DiaryMood
import com.codex.appgoodwords.data.MoodPracticeRow
import com.codex.appgoodwords.data.OnThisDayMemory
import com.codex.appgoodwords.data.RoutineCheckEntity
import com.codex.appgoodwords.data.RoutineEntity
import com.codex.appgoodwords.data.RoutineNudge
import com.codex.appgoodwords.data.StatsSummary
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch

internal const val feedbackCardTag = "home_feedback_card"
internal const val growthTeaserTag = "home_growth_teaser"
internal const val onThisDayCardTag = "home_on_this_day"
internal const val moodPracticeCardTag = "home_mood_practice"
internal const val homeCaptureButtonTag = "home_capture_button"
internal const val homeInsightsToggleTag = "home_insights_toggle"
internal const val homeListTag = "home_list"
internal const val homeTabRowTag = "home_tab_row"
internal const val homePagerTag = "home_pager"
/** 한마디 칸 이름에 붙는 점. 새 한마디가 와 있다는 뜻입니다. */
internal const val homeCoachDotTag = "home_coach_dot"

internal fun homeTabTag(tab: HomeTab): String = "home_tab_" + tab.name.lowercase()

/**
 * 글귀 아래의 칸. 좌우로 밀어 넘깁니다(사용자가 고름, 2026-10-28).
 *
 * 한 장에 카드 열 개가 이어져 있어 난잡했습니다. 하루를 밟는 것(오늘), 건네받는 말(한마디), 쌓인 것을 보는 것
 * (돌아보기)으로 나눠 한 번에 한 묶음만 보입니다. 적어 둔 차례가 곧 화면의 차례입니다.
 */
enum class HomeTab(val label: String) {
    TODAY("오늘"),
    COACH("한마디"),
    LOOK_BACK("돌아보기");

    companion object {
        /** 한마디를 끈 사람에게는 그 칸을 두지 않습니다. 켜 보라는 칸이 매일 보이면 고르는 자리가 아니라 권유가 됩니다. */
        fun visible(hasCoach: Boolean): List<HomeTab> = if (hasCoach) entries else listOf(TODAY, LOOK_BACK)
    }
}

/**
 * 홈.
 *
 * **맨 위는 오늘의 글귀입니다**(사용자가 정함, 2026-10-28). 예전에는 읽는 일을 보관함으로 보내고 홈에는 "지금 어떻게
 * 지내는지"만 남겼는데, 앱 이름이 "오늘의 글귀"인데 글귀가 보이지 않았고 카드 열 개가 한 장에 이어져 난잡했습니다.
 * 이제 글귀를 크게 두고([TodayQuoteCard]) 그 아래를 [HomeTab] 셋으로 나눠 좌우로 밀어 넘깁니다. 칸 이름 줄은
 * 아래로 굴려도 위에 붙어 있어 어디서든 옆 칸으로 옮겨 갈 수 있습니다.
 *
 * 홈을 왼쪽으로 밀어 아이디어 칸을 띄우던 몸짓은 칸 넘기기와 겹쳐 없앴습니다. 맨 위의 "아이디어 담기"와 위젯의
 * "적기"가 그 자리를 맡습니다. 한 줄로 적던 "번뜩인 것" 카드도 같은 일을 하는 단추가 바로 위에 있어 거뒀습니다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    summary: StatsSummary,
    notes: List<FeedbackNote>,
    routines: List<RoutineEntity>,
    checks: List<RoutineCheckEntity>,
    modifier: Modifier = Modifier,
    /** 오늘의 글귀(위젯·알림과 같은 것). null이면 아직 읽는 중입니다. */
    quote: HomeQuote? = null,
    quoteConfirmedToday: Boolean = false,
    /** 읽음. 오늘의 걸음 "글귀 읽기"를 채웁니다. 다시 누르면 거둡니다. */
    onConfirmQuote: (ContentItemEntity) -> Unit = {},
    onNextQuote: () -> Unit = {},
    onOpenQuote: (ContentItemEntity) -> Unit = {},
    /** 보관함에 글귀가 하나도 없을 때 담으러 갑니다. */
    onAddQuote: () -> Unit = {},
    /** 되고 싶은 나와 목표·롤모델 하나([com.codex.appgoodwords.data.GoalTracking.homeLine]). 목표 탭이 비었으면 null입니다. */
    goalLine: HomeGoalLine? = null,
    onOpenGoal: (Long) -> Unit = {},
    /** 오늘의 걸음. null이면 카드를 두지 않습니다. */
    dailyLoop: DailyProgress? = null,
    /** 걸음을 누르면 그 화면으로 데려갑니다. 알려만 주면 다시 찾아 들어가야 합니다. */
    onOpenStep: (DailyStep) -> Unit = {},
    /** 가장 최근에 AI가 써 준 돌아보기. 없으면 권하는 카드만 둡니다. */
    latestReport: GrowthReportEntity? = null,
    onOpenGrowth: () -> Unit = {},
    /** 지난 이맘때 남긴 것. 없으면 카드를 두지 않습니다. */
    memories: List<OnThisDayMemory> = emptyList(),
    onOpenMemory: (OnThisDayMemory) -> Unit = {},
    /** 기분별 실천. 날이 적으면 빈 목록입니다. */
    moodPractice: List<MoodPracticeRow> = emptyList(),
    /** 오늘의 기분. 찍어 둔 것이 없고 일기에도 없으면 null입니다. */
    todayMood: DiaryMood? = null,
    onPickMood: (DiaryMood) -> Unit = {},
    /** 찍어 둔 것이 있을 때만 지울 수 있습니다. */
    onClearMood: (() -> Unit)? = null,
    /** 번뜩인 것을 담는 칸을 띄웁니다. 여기가 앱에서 가장 빨리 닿는 자리입니다. */
    onOpenCapture: () -> Unit = {},
    /**
     * 오늘의 한마디. null이면 한마디 칸을 두지 않습니다 — 설정에서 켜지 않은 상태입니다.
     *
     * 끈 사람의 홈에 "켜 보세요"를 매일 띄우지 않습니다. 값이 드는 기능이라 권유가 아니라 고르는 자리(설정)에 둡니다.
     */
    coach: DailyCoachState? = null,
    /** 오늘 한 것이 있는지. 없으면 한마디 대신 그 사실을 적습니다. */
    coachHasRecordToday: Boolean = true,
    coachLoading: Boolean = false,
    /** 내 말에 답을 받는 중. 카드의 마지막 한 쌍 자리에 "답을 쓰는 중"을 적습니다. */
    coachReplying: Boolean = false,
    /** 아직 안 밟은 루틴 한 줄. AI 없이 기기에서 센 것이라 연결이 없어도 뜹니다. */
    coachNudge: RoutineNudge? = null,
    /** 다음 한마디가 저절로 올 때. 카드가 "다음 한마디 15:20쯤"으로 알립니다. 모르면 null입니다. */
    coachNextAt: Long? = null,
    onOpenRoutines: () -> Unit = {},
    onRefreshCoach: () -> Unit = {},
    onKeepCoachQuote: () -> Unit = {},
    onKeepCoachRoutine: () -> Unit = {},
    onKeepCoachAction: () -> Unit = {},
    /** 새로 온 한마디를 다 보여 주었습니다. */
    onCoachSeen: () -> Unit = {},
    /** 한마디에 답하러 대화 화면으로 갑니다. */
    onReplyCoach: () -> Unit = {},
    /**
     * 한마디 칸을 열어 보여 달라는 요청. 알림이나 "새 한마디 · 보기"를 눌러 왔을 때입니다.
     *
     * 한마디는 글귀 아래 옆 칸에 있어 홈만 열면 보이지 않습니다. 보러 온 것을 다시 찾게 하지 않습니다.
     * 칸을 열고 칸 이름 줄을 위로 올린 뒤 [onCoachFocused]로 알립니다.
     */
    coachFocusRequested: Boolean = false,
    onCoachFocused: () -> Unit = {},
    /** 오늘 점수와 최근 흐름. null이면 카드를 두지 않습니다. */
    todayScore: DayScore? = null,
    scoreTrend: ScoreTrend? = null,
    /** 그날 요약을 펼칩니다. 그래프의 막대를 눌러도 옵니다. */
    onOpenDay: (LocalDate) -> Unit = {}
) {
    var selectedMonthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedDateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    // 칸을 넘기면 안 보이는 칸은 버려집니다. 펼쳐 둔 통계가 옆 칸에 다녀오면 접혀 있지 않게 여기서 들고 있습니다.
    var showInsights by rememberSaveable { mutableStateOf(false) }

    val tabs = HomeTab.visible(hasCoach = coach != null)
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // 걸음의 "글귀 읽기"를 누른 횟수. 누를 때마다 글귀 카드에 잠깐 테두리를 두릅니다.
    var quotePulse by remember { mutableIntStateOf(0) }

    // 앱을 막 띄웠을 때는 켜 둔 설정을 읽기 전이라 한마디 칸이 아직 없습니다. 칸이 생기면 그때 엽니다.
    // **목록부터 내립니다.** 글귀가 길거나 화면이 작아 칸들이 화면 밖에 있으면 아직 그려지지 않은 것이라,
    // 칸을 먼저 넘기려 하면 그려질 때까지 기다리느라 멈춥니다.
    LaunchedEffect(coachFocusRequested, coach != null) {
        if (!coachFocusRequested || coach == null) return@LaunchedEffect
        listState.animateScrollToItem(TAB_ROW_ITEM_INDEX)
        pagerState.animateScrollToPage(tabs.indexOf(HomeTab.COACH))
        onCoachFocused()
    }

    // 칸 이름을 누르면 그 칸으로. 칸들이 아직 화면 밖이면(칸 이름 줄이 화면 맨 아래에 걸쳐 있을 때) 먼저 올려 보여 줍니다.
    fun openTab(index: Int) {
        scope.launch {
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == PAGES_ITEM_INDEX }) {
                listState.animateScrollToItem(TAB_ROW_ITEM_INDEX)
            }
            pagerState.animateScrollToPage(index)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag(homeListTag),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item(key = "date") {
            DateRow(onOpenCapture = onOpenCapture)
        }

        // 앱을 열면 가장 먼저 보이는 자리입니다. 이 앱이 무엇인지가 여기서 드러납니다.
        item(key = "quote") {
            TodayQuoteCard(
                quote = quote,
                confirmedToday = quoteConfirmedToday,
                onConfirm = onConfirmQuote,
                onNext = onNextQuote,
                onOpen = onOpenQuote,
                onAdd = onAddQuote,
                pulseKey = quotePulse,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        // 이 자리를 `TAB_ROW_ITEM_INDEX`가 셉니다. 위에 줄을 더하면 함께 고칩니다.
        stickyHeader(key = "tabs") {
            HomeTabRow(
                tabs = tabs,
                pagerState = pagerState,
                coachHasNew = coach?.hasNewTip == true,
                onSelect = { index -> openTab(index) }
            )
        }

        item(key = "pages") {
            HorizontalPager(
                state = pagerState,
                key = { page -> tabs[page] },
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(homePagerTag)
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (tabs[page]) {
                        // 오늘 무엇부터 할지와 오늘 어땠는지. 매일 손이 가는 둘이라 첫 칸입니다.
                        HomeTab.TODAY -> {
                            // 무엇을 향해 가는지가 오늘 무엇부터 할지보다 앞에 놓입니다(사용자가 정함, 2026-10-29).
                            // 목표 탭에 아무것도 없으면 자리를 내지 않습니다 — 적어 보라는 카드가 매일 뜨면 잔소리가 됩니다.
                            goalLine?.let { line -> HomeGoalCard(line = line, onOpen = onOpenGoal) }
                            dailyLoop?.let { progress ->
                                DailyLoopCard(
                                    progress = progress,
                                    onOpenStep = { step ->
                                        // 글귀 읽기는 이 화면 맨 위에 있습니다. 보관함으로 데려가지 않고 그 카드를 보여 줍니다.
                                        if (step == DailyStep.QUOTE) {
                                            quotePulse += 1
                                            scope.launch { listState.animateScrollToItem(QUOTE_ITEM_INDEX) }
                                        } else {
                                            onOpenStep(step)
                                        }
                                    }
                                )
                            }
                            // 3초짜리라 걸음 바로 아래에 둡니다. 오늘 무엇을 했는지 옆에 오늘 어땠는지가 나란히 놓입니다.
                            TodayMoodCard(mood = todayMood, onPick = onPickMood, onClear = onClearMood)
                        }

                        // 코치가 보는 것은 오늘 칸의 걸음과 기분입니다. 그래서 바로 옆 칸입니다.
                        HomeTab.COACH -> coach?.let { state ->
                            DailyCoachCard(
                                state = state,
                                loading = coachLoading,
                                replying = coachReplying,
                                hasRecordToday = coachHasRecordToday,
                                nudge = coachNudge,
                                nextAskAt = coachNextAt,
                                onOpenRoutines = onOpenRoutines,
                                onRefresh = onRefreshCoach,
                                onKeepQuote = onKeepCoachQuote,
                                onKeepRoutine = onKeepCoachRoutine,
                                onKeepAction = onKeepCoachAction,
                                onSeen = onCoachSeen,
                                onReply = onReplyCoach
                            )
                        }

                        // 앞에서부터 시간의 폭이 넓어집니다 — 오늘 점수, 요즘, 지난 이맘때, AI가 정리한 기간, 통계.
                        HomeTab.LOOK_BACK -> {
                            if (todayScore != null && scoreTrend != null) {
                                DayScoreCard(
                                    todayScore = todayScore,
                                    trend = scoreTrend,
                                    onOpenDay = onOpenDay
                                )
                            }

                            FeedbackCard(notes = notes)

                            // 없는 날이 대부분이라 있을 때만 자리를 냅니다. 빈 카드가 매일 있으면
                            // "오늘은 없음"을 매일 확인하게 됩니다.
                            if (memories.isNotEmpty()) {
                                OnThisDayCard(memories = memories, onOpen = onOpenMemory)
                            }

                            GrowthTeaserCard(report = latestReport, onOpen = onOpenGrowth)

                            InsightsToggle(expanded = showInsights, onToggle = { showInsights = !showInsights })

                            if (showInsights) {
                                StatsCard(summary = summary)
                                if (moodPractice.isNotEmpty()) {
                                    MoodPracticeCard(rows = moodPractice)
                                }
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
                        }
                    }
                }
            }
        }
    }
}

/** 오늘 날짜와, 번뜩인 것을 담는 단추. */
@Composable
private fun DateRow(onOpenCapture: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = LocalDate.now().format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        FilledTonalButton(
            onClick = onOpenCapture,
            modifier = Modifier.testTag(homeCaptureButtonTag)
        ) {
            Text("아이디어 담기")
        }
    }
}

/**
 * 칸 이름 줄. 아래로 굴려도 위에 붙어 있습니다.
 *
 * 밑줄이 손가락을 따라 옆 칸으로 미끄러집니다. 다 넘긴 뒤에야 옮겨 가면 지금 어느 칸으로 가는지 손이 먼저 압니다.
 * 한마디가 새로 와 있으면 그 칸 이름에 점을 답니다(하단 바의 홈 점과 같은 표시).
 */
@Composable
private fun HomeTabRow(
    tabs: List<HomeTab>,
    pagerState: PagerState,
    coachHasNew: Boolean,
    onSelect: (Int) -> Unit
) {
    val current = pagerState.currentPage.coerceIn(0, tabs.lastIndex)
    // 굴린 글이 비치지 않게 바탕을 칠합니다. 칸 이름 줄만 칠하면 위쪽 틈으로 카드가 비쳐 보입니다.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 10.dp)
    ) {
        TabRow(
            selectedTabIndex = current,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { positions -> PagerTabIndicator(positions = positions, pagerState = pagerState) },
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) },
            modifier = Modifier.testTag(homeTabRowTag)
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = current == index,
                    onClick = { onSelect(index) },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(homeTabTag(tab)),
                    text = {
                        val label: @Composable () -> Unit = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (current == index) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                        if (tab == HomeTab.COACH && coachHasNew) {
                            BadgedBox(badge = { Badge(modifier = Modifier.testTag(homeCoachDotTag)) }) { label() }
                        } else {
                            label()
                        }
                    }
                )
            }
        }
    }
}

/** 밑줄. 칸 너비의 절반만 그어 이름 아래에 모이게 합니다. 꽉 채우면 칸 사이 경계선처럼 보입니다. */
@Composable
private fun PagerTabIndicator(positions: List<TabPosition>, pagerState: PagerState) {
    if (positions.isEmpty()) return
    val page = pagerState.currentPage.coerceIn(0, positions.lastIndex)
    val fraction = pagerState.currentPageOffsetFraction
    val toward = (page + if (fraction > 0f) 1 else -1).coerceIn(0, positions.lastIndex)
    val left = lerp(positions[page].left, positions[toward].left, abs(fraction))
    val tabWidth = lerp(positions[page].width, positions[toward].width, abs(fraction))
    val lineWidth = tabWidth / 2
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentSize(Alignment.BottomStart)
            .offset(x = left + (tabWidth - lineWidth) / 2)
            .width(lineWidth)
            .height(3.dp)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
    )
}

/** 통계와 실천 달력. 길어서 접어 둡니다. 돌아보기 칸 맨 아래입니다. */
@Composable
private fun InsightsToggle(expanded: Boolean, onToggle: () -> Unit) {
    TextButton(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(homeInsightsToggleTag)
            .semantics { stateDescription = if (expanded) "펼쳐짐" else "접힘" }
    ) {
        Text(
            text = if (expanded) "통계와 실천 달력 접기" else "통계와 실천 달력 보기",
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null
        )
    }
}

/**
 * 기록을 문장으로 짚어 주는 카드.
 *
 * 숫자는 아래 통계 카드가 맡습니다. 여기서는 그 숫자가 무슨 뜻인지 한 줄로 옮깁니다.
 * 못 한 것을 세지 않습니다. 짚는 말은 [FeedbackWriter]가 한 줄까지만 담아 줍니다.
 */
@Composable
private fun FeedbackCard(notes: List<FeedbackNote>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(feedbackCardTag),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "요즘 이렇게 지내고 있어요",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (notes.isEmpty()) {
                Text(
                    text = "기록이 쌓이면 여기에 짚어 드릴 말이 생깁니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                notes.forEach { note ->
                    FeedbackLine(note = note)
                }
            }
        }
    }
}

@Composable
private fun FeedbackLine(note: FeedbackNote) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 점 하나로 성격을 나눕니다. 아이콘을 붙이면 세 줄이 저마다 다른 그림이 되어 시끄럽습니다.
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(note.kind.dotColor())
        )
        Text(
            text = note.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 짚는 말도 붉게 칠하지 않습니다.
 *
 * 경고 색을 쓰면 뜸해진 루틴 하나가 화면에서 사고처럼 보입니다.
 */
@Composable
private fun FeedbackKind.dotColor(): Color = when (this) {
    FeedbackKind.STREAK -> MaterialTheme.colorScheme.primary
    FeedbackKind.PRAISE -> MaterialTheme.colorScheme.tertiary
    FeedbackKind.NUDGE -> MaterialTheme.colorScheme.outline
    FeedbackKind.INVITE -> MaterialTheme.colorScheme.secondary
}

/**
 * AI가 써 준 돌아보기로 가는 문.
 *
 * 홈에 글 전체를 펼치지 않습니다. 잘한 점 한 줄만 보이고 나머지는 그 화면에서 봅니다.
 * 홈은 훑는 자리이지 읽는 자리가 아닙니다.
 */
@Composable
private fun GrowthTeaserCard(
    report: GrowthReportEntity?,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(growthTeaserTag),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "AI 돌아보기",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = report?.strengths?.firstOrNull()
                    ?: report?.guide?.takeIf { it.isNotBlank() }
                    ?: "기록을 보고 잘한 점과 다음 걸음을 정리해 드립니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            // 이 칸의 주인공은 기록이라 단추를 덜 무겁게 둡니다. 가득 칠한 단추는 홈 맨 위의 "읽음" 하나입니다.
            FilledTonalButton(onClick = onOpen) {
                Text(if (report == null) "받아 보기" else "돌아보기 열기")
            }
        }
    }
}

/**
 * 지난 이맘때 남긴 것.
 *
 * 일기와 글귀는 쌓일수록 값이 커지는 기록인데, 지금까지 홈은 최근 이레와 이번 달만 보여
 * 주었습니다. 오래 쓴 사람에게만 생기는 되돌림을 여기에 둡니다.
 * 없는 날이 대부분이라 **아무것도 없으면 카드 자체를 두지 않습니다.** 빈 카드가 매일 자리를
 * 차지하면 "오늘은 없음"을 매일 확인하게 됩니다.
 */
@Composable
private fun OnThisDayCard(
    memories: List<OnThisDayMemory>,
    onOpen: (OnThisDayMemory) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(onThisDayCardTag),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "지난 이맘때",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            memories.forEach { memory ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(memory) },
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "${memory.whenText} · ${memory.kind.label}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = memory.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (memory.body.isNotBlank()) {
                        Text(
                            text = memory.body,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * 기분과 실천을 나란히 놓은 줄.
 *
 * **인과로 말하지 않습니다.** 많이 움직여서 기분이 좋았는지 그 반대인지는 이 숫자로 알 수
 * 없습니다. "이런 날엔 이만큼 움직였습니다"까지만 적습니다.
 */
@Composable
private fun MoodPracticeCard(rows: List<MoodPracticeRow>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(moodPracticeCardTag),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "기분과 실천",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "그 기분으로 적은 날, 하루에 평균 몇 번 움직였는지입니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = row.mood.emoji, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = row.mood.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "%.1f회".format(row.averagePerDay),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${row.dayCount}일",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 글귀 카드의 자리(날짜 줄 다음). 걸음의 "글귀 읽기"를 누르면 여기까지 올립니다. */
private const val QUOTE_ITEM_INDEX = 1

/** 칸 이름 줄의 자리(날짜 줄, 글귀 다음). 한마디 칸으로 데려갈 때 여기까지 굴려 칸 이름 줄을 맨 위에 붙입니다. */
private const val TAB_ROW_ITEM_INDEX = 2

/** 칸들이 놓인 자리(칸 이름 줄 다음). */
private const val PAGES_ITEM_INDEX = 3
