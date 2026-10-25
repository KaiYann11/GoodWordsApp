package com.codex.appgoodwords.data

import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 오늘 기록을 추려 코치에게 묻고, 받은 한마디를 이 기기에 적어 두기까지.
 *
 * 화면이 눌러서 부르든 홈이 열리며 저절로 부르든 같은 길을 쓰게 하려고 한곳에 모았습니다
 * ([GrowthFeedbackCoordinator]와 같은 자리). 무엇이 나가고 무엇이 남는지가 같아야 합니다.
 *
 * **어디에 물어볼지는 [AiFeedbackClient]가 정합니다.** 서버 주소가 있으면 서버를 거치고
 * (서버는 열쇠로 API를 부르거나 로그인해 둔 CLI를 돌립니다), 없으면 이 기기에 넣어 둔
 * 열쇠로 직접 부릅니다. 돌아보기와 **같은 설정을 그대로 씁니다** — 자리마다 공급자와 열쇠를
 * 따로 고르게 하면 설정이 두 배가 되고, 어느 쪽이 어디로 나가는지 알기 어려워집니다.
 *
 * **예외를 밖으로 던지지 않습니다.** 저절로 도는 일이라 받아 줄 화면이 없을 때가 있어,
 * 실패는 [DailyCoachState.lastError]에 적어 카드가 그대로 보여 줍니다.
 */
class DailyCoachCoordinator(
    private val database: AppDatabase,
    private val settingsStore: SettingsStore,
    private val repository: AppRepository,
    private val client: AiFeedbackClient = AiFeedbackClient(),
    /**
     * 화면을 떠나 끊긴 물음을 배경 일꾼에게 넘깁니다([refresh]의 `interrupted`).
     *
     * 인터넷을 조건으로 건 일꾼에게는 Android 15도 앱 밖에서 통신을 열어 줍니다. 그래서 앱을 나간 뒤에도
     * 한마디가 알림으로 옵니다.
     */
    private val handOver: () -> Unit = {}
) {
    /** 한 번에 하나만 묻습니다([refresh]). */
    private val askLock = Mutex()

    /** 설정에서 "무엇이 나가는지" 보여 줄 글. 실제로 보내는 것과 같은 함수로 만듭니다. */
    suspend fun preview(
        today: LocalDate = LocalDate.now(),
        at: LocalTime = LocalTime.now()
    ): String {
        val includeDiaryBody = settingsStore.getAiFeedbackSettings().includeDiaryBody
        return DailyCoachPrompt.chatPrompt(buildDigest(today, at, includeDiaryBody))
    }

    /** 받아 둔 것을 오늘 것으로만 추려 돌려줍니다. 날이 바뀌었으면 빈 상태입니다. */
    suspend fun state(today: LocalDate = LocalDate.now()): DailyCoachState =
        settingsStore.getDailyCoachState().forDate(today)

    /**
     * 받아 둔 한마디를 화면에서 보았다고 적습니다.
     *
     * 그래야 카드의 반짝임과 홈 탭의 점이 거둬집니다. 이미 본 것이면 저장하지 않습니다 — 홈을 열 때마다
     * 설정을 다시 쓰면 그 흐름을 보는 화면이 괜히 다시 그려집니다.
     */
    suspend fun markSeen(today: LocalDate = LocalDate.now()) {
        val state = state(today)
        if (state.hasNewTip) save(state.markSeen())
    }

    /**
     * 한마디를 받아 옵니다.
     *
     * **한 번에 하나만 묻습니다.** 홈과 배경 일꾼이 같은 때 들여다보면 둘 다 "물을 때"로 읽어 두 번 나갑니다.
     * 뒤에 온 쪽은 앞의 물음이 끝나기를 기다렸다가 다시 봅니다 — 대개는 물을 때가 지나 있어 그냥 돌아갑니다.
     *
     * @param force 사용자가 직접 눌렀는지. 그때는 같은 기록이어도, 틈이 덜 벌어졌어도 묻습니다.
     *   자기가 눌러 자기 몫을 쓰는 것이라 앱이 말릴 일이 아닙니다. 다만 오늘 한 것이 하나도
     *   없으면 그때도 묻지 않습니다 — 보낼 기록이 없습니다.
     * @param interrupted 묻던 중에 앱이 화면을 떠났는지. 홈이 넘깁니다 — 그때 끊긴 연결은 세지 않고
     *   ([DailyCoachState.interrupted]) 배경 일꾼에게 넘깁니다([handOver]). 배경 일꾼은 화면과 상관없이 돕니다.
     * @return 새로 적힌 상태. 물을 때가 아니었으면 들고 있던 것 그대로입니다.
     */
    suspend fun refresh(
        force: Boolean = false,
        today: LocalDate = LocalDate.now(),
        at: LocalTime = LocalTime.now(),
        now: Long = System.currentTimeMillis(),
        interrupted: () -> Boolean = { false }
    ): DailyCoachState = askLock.withLock { ask(force, today, at, now, interrupted) }

    private suspend fun ask(
        force: Boolean,
        today: LocalDate,
        at: LocalTime,
        now: Long,
        interrupted: () -> Boolean
    ): DailyCoachState {
        val aiSettings = settingsStore.getAiFeedbackSettings()
        val syncSettings = settingsStore.getServerSyncSettings()
        val stored = settingsStore.getDailyCoachState().forDate(today)
        // 꺼 두었으면 아무것도 하지 않습니다. 켜는 것은 사용자가 정합니다.
        if (!aiSettings.coachEnabled) return stored

        val digest = buildDigest(today, at, aiSettings.includeDiaryBody)
        if (digest.isEmpty) {
            // 눌러서 부른 것이면 왜 아무 일도 없는지 말해 주어야 합니다. 저절로 도는 길에서는
            // 아직 아무것도 안 한 아침마다 사유를 띄울 일이 아니라 조용히 지나갑니다.
            return if (force) save(stored.withError(EMPTY_TODAY)) else stored
        }
        if (!aiSettings.canAsk(hasServer = syncSettings.serverUrl.isNotBlank())) {
            return if (force) save(stored.withError(NO_CONNECTION)) else stored
        }
        // 몇 번까지, 얼마 만에 물을지는 설정에서 고른 대로입니다.
        val due = stored.needsAsk(
            fingerprint = digest.fingerprint,
            now = now,
            limit = aiSettings.askLimit,
            gapMinutes = aiSettings.gapMinutes
        )
        if (!force && !due) return stored

        // 물어본 사실부터 적습니다. 실패해도 남아야 다음 물음이 틈을 둡니다.
        save(stored.asked(today, now))
        // 답은 그때의 상태(`state(today)`)에 얹습니다. 묻는 데 몇 분이 걸리기도 해서, 그 사이 눌러 둔
        // 담기·보기를 묻기 전의 것으로 덮지 않게 합니다.
        val rawText = try {
            client.ask(
                system = DailyCoachPrompt.systemPrompt(),
                user = DailyCoachPrompt.userPrompt(digest),
                model = aiSettings.effectiveModel,
                aiSettings = aiSettings,
                syncSettings = syncSettings
            )
        } catch (failure: Exception) {
            // 연결이 끊긴 것이 화면을 떠난 탓이면(Android 15는 화면 밖으로 나간 앱의 통신을 끊습니다) 세지 않고
            // 넘깁니다. "인터넷 연결을 확인해 주세요"는 맞지 않는 말이라 남기지 않습니다.
            if (failure is IOException && interrupted()) {
                val restored = save(state(today).interrupted(stored))
                if (aiSettings.coachRunsInBackground) handOver()
                return restored
            }
            return save(state(today).withError(failure.message ?: "오늘의 한마디를 받지 못했습니다."))
        }

        val tip = DailyCoachParser.parse(rawText, model = aiSettings.reportModel, now = now)
            ?: return save(state(today).withError("코치가 보낸 답을 읽지 못했습니다."))
        return save(state(today).withTip(tip, digest.fingerprint))
    }

    /**
     * 권해 받은 글귀를 보관함에 담습니다.
     *
     * **담은 것은 다시 담지 않습니다.** 카드의 버튼은 스치듯 누르는 자리라, 두 번 누르면
     * 같은 글귀가 두 벌 쌓입니다.
     */
    suspend fun keepQuote(today: LocalDate = LocalDate.now()) {
        val state = state(today)
        val tip = state.tip
        require(tip != null && tip.quote.isNotBlank()) { "담을 글귀가 없습니다." }
        check(!state.quoteKept) { "이미 담았습니다." }
        repository.saveContent(
            ContentDraft(
                type = ContentType.QUOTE,
                title = tip.quote.take(QUOTE_TITLE_LIMIT),
                body = tip.quote,
                author = tip.quoteAuthor,
                category = AI_CATEGORY
            )
        )
        save(state.copy(quoteKept = true))
    }

    suspend fun keepRoutine(today: LocalDate = LocalDate.now()) {
        val state = state(today)
        val title = state.tip?.routine.orEmpty()
        require(title.isNotBlank()) { "만들 루틴이 없습니다." }
        check(!state.routineKept) { "이미 만들었습니다." }
        repository.saveRoutine(RoutineDraft(title = title, category = AI_CATEGORY))
        save(state.copy(routineKept = true))
    }

    /** 해 볼 한 가지는 **오늘** 할 일로 담습니다. 내일로 미루라고 받은 말이 아닙니다. */
    suspend fun keepAction(today: LocalDate = LocalDate.now()) {
        val state = state(today)
        val title = state.tip?.action.orEmpty()
        require(title.isNotBlank()) { "담을 할 일이 없습니다." }
        check(!state.actionKept) { "이미 담았습니다." }
        repository.saveTodo(TodoDraft(title = title, dueDate = today))
        save(state.copy(actionKept = true))
    }

    private suspend fun save(state: DailyCoachState): DailyCoachState {
        settingsStore.setDailyCoachState(state)
        return state
    }

    private suspend fun buildDigest(
        today: LocalDate,
        at: LocalTime,
        includeDiaryBody: Boolean,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): DailyCoachDigest {
        val events = database.exposureEventDao().getAll()
        val routines = database.routineDao().getAll()
        val routineChecks = database.routineCheckDao().getAll()
        val todos = database.todoDao().getAll()
        val diaries = database.diaryDao().getAll()
        val moodLogs = database.moodLogDao().getAll()
        val readingLogs = database.bookReadingLogDao().getAll()
        val steps = settingsStore.getDailySteps()

        return DailyCoachPrompt.digest(
            today = today,
            at = at,
            // 그날 카드와 같은 셈을 씁니다. 여기서 따로 세면 화면과 코치가 다른 하루를 봅니다.
            dayDigest = DayScoreCalculator.digest(
                date = today,
                events = events,
                routineChecks = routineChecks,
                todos = todos,
                diaries = diaries,
                moodLogs = moodLogs,
                steps = steps,
                readingLogs = readingLogs,
                zoneId = zoneId
            ),
            progress = DailyLoopCalculator.build(
                events = events,
                routineChecks = routineChecks,
                todos = todos,
                diaries = diaries,
                today = today,
                steps = steps,
                readingLogs = readingLogs,
                zoneId = zoneId
            ),
            routines = routines,
            todayChecks = routineChecks.filter { toDate(it.checkedAt, zoneId) == today },
            diaries = diaries,
            items = database.contentItemDao().getAll(),
            includeDiaryBody = includeDiaryBody
        )
    }

    private fun toDate(timestamp: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()

    companion object {
        /** 글귀 제목은 앞머리만 씁니다. 본문은 그대로 담깁니다. */
        private const val QUOTE_TITLE_LIMIT = 40

        const val EMPTY_TODAY = "오늘 기록이 아직 없습니다. 한 걸음 밟고 나서 받아 보세요."
        const val NO_CONNECTION = "AI에 닿을 길이 없습니다. 설정에서 서버 주소나 열쇠를 넣어 주세요."
    }
}
