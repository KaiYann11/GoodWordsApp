package com.codex.appgoodwords.data

import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * 오늘 하루를 서버의 CLI에 물어 한마디를 받아 오는 길을 끝까지 밟습니다.
 *
 * 앱 → HTTPS → 서버 `/api/growth-feedback` → `claude`/`codex` → 답 해석.
 * 서버에 로그인해 둔 CLI가 실제로 답하므로 값이 조금 듭니다. `testServerUrl`이 없으면 건너뜁니다 —
 * 운영 서버가 아니라 **CLI가 깔린 PC에서 띄운 별도 HTTPS 테스트 서버**를 가리켜야 합니다.
 *
 * 여기서 보려는 것은 **일러 준 형식이 실제로 지켜지는지**입니다. 형식이 어긋나면 카드에는
 * 아무것도 안 뜨는데, 그 사실은 사람이 켜 보기 전에는 드러나지 않습니다. 한마디에 내가 답한 말의 답도 같은 길로 봅니다.
 */
class DailyCoachAiIntegrationTest {
    private val syncSettings = ServerSyncSettings(
        InstrumentationRegistry.getArguments().getString("testServerUrl").orEmpty(),
        InstrumentationRegistry.getArguments().getString("testServerApiKey").orEmpty()
    )

    private val today = LocalDate.now()

    @Before fun setUp() {
        assumeTrue("CLI가 깔린 PC의 HTTPS 테스트 서버가 필요합니다.", syncSettings.serverUrl.isNotBlank())
    }

    @Test fun claudeCliAnswersWithOneShortWord() = runBlocking {
        askWith(AiFeedbackSettings(provider = AiProvider.CLAUDE_CLI.name, model = "claude-haiku-5-5"))
    }

    @Test fun codexCliWithItsDefaultModelAnswers() = runBlocking {
        askWith(AiFeedbackSettings(provider = AiProvider.CODEX_CLI.name, model = AiProvider.CLI_DEFAULT_MODEL))
    }

    @Test fun claudeCliAnswersWhatISaid() = runBlocking {
        replyWith(AiFeedbackSettings(provider = AiProvider.CLAUDE_CLI.name, model = "claude-haiku-5-5"))
    }

    @Test fun codexCliAnswersWhatISaid() = runBlocking {
        replyWith(AiFeedbackSettings(provider = AiProvider.CODEX_CLI.name, model = AiProvider.CLI_DEFAULT_MODEL))
    }

    /**
     * 한마디에 내가 답한 말에 코치가 이어 답하는지(사용자가 정함, 2026-10-29). 답 형식이 어긋나도 글로는 받지만, 그러면
     * 해 볼 일을 담을 수 없어 형식까지 봅니다.
     */
    private suspend fun replyWith(aiSettings: AiFeedbackSettings) {
        val now = System.currentTimeMillis()
        val talk = listOf(
            DailyCoachLine(
                kind = DailyCoachLine.Kind.TIP,
                text = "물 한 컵으로 하루를 여셨네요. 저녁에는 잠깐 걸어 보세요.",
                action = "저녁 산책 10분",
                at = now - 30 * 60_000L
            ),
            DailyCoachLine(kind = DailyCoachLine.Kind.REPLY, text = "오늘은 다리가 아파서 걷기는 어려울 것 같아요.", at = now)
        )
        val rawAnswer = AiFeedbackClient().ask(
            system = DailyCoachPrompt.replySystemPrompt(),
            user = DailyCoachPrompt.replyUserPrompt(digest(talk)),
            model = aiSettings.effectiveModel,
            aiSettings = aiSettings,
            syncSettings = syncSettings
        )

        val answer = DailyCoachParser.parseAnswer(rawAnswer, model = aiSettings.reportModel)
        assertNotNull("답이 오지 않았습니다: $rawAnswer", answer)
        assertTrue(rawAnswer, answer!!.text.isNotBlank())
        assertTrue("일러 준 형식이 아닙니다: $rawAnswer", rawAnswer.contains("\"message\""))
        // 대화 화면의 말풍선에 들어가야 합니다. 길게 오면 해석에서 잘립니다.
        assertTrue(answer.text, answer.text.length <= 241)
    }

    private suspend fun askWith(aiSettings: AiFeedbackSettings) {
        val digest = digest()
        val rawAnswer = AiFeedbackClient().ask(
            system = DailyCoachPrompt.systemPrompt(),
            user = DailyCoachPrompt.userPrompt(digest),
            model = aiSettings.effectiveModel,
            aiSettings = aiSettings,
            syncSettings = syncSettings
        )

        val tip = DailyCoachParser.parse(rawAnswer, model = aiSettings.reportModel)
        assertNotNull("형식에 맞는 한마디가 오지 않았습니다: $rawAnswer", tip)
        assertTrue(rawAnswer, tip!!.message.isNotBlank())
        // 홈 카드에 들어가야 합니다. 길게 오면 해석에서 잘립니다.
        assertTrue(tip.message, tip.message.length <= 161)
        // 일기는 켜지 않았으므로 본문이 나가지 않았습니다. 받은 말에도 묻어 있으면 안 됩니다.
        assertFalse(tip.message, tip.message.contains("아무에게도"))
    }

    /** 오늘 한 것이 조금 있는 하루. 빈 하루로는 묻지 않습니다. */
    private fun digest(talk: List<DailyCoachLine> = emptyList()): DailyCoachDigest {
        val checks = listOf(
            RoutineCheckEntity(
                syncId = "c1",
                routineId = 1,
                routineSyncId = "r1",
                routineTitle = "물 한 컵",
                checkedAt = System.currentTimeMillis()
            )
        )
        val diaries = listOf(
            DiaryEntity(
                id = 1,
                syncId = "d1",
                entryDate = today.toString(),
                title = "아무에게도 못 한 말",
                body = "아무에게도 하지 못한 이야기가 있다.",
                mood = "TIRED"
            )
        )
        val steps = listOf(DailyStep.QUOTE, DailyStep.ROUTINE, DailyStep.DIARY)

        return DailyCoachPrompt.digest(
            today = today,
            at = LocalTime.now(),
            dayDigest = DayScoreCalculator.digest(
                date = today,
                events = emptyList(),
                routineChecks = checks,
                todos = emptyList(),
                diaries = diaries,
                steps = steps
            ),
            progress = DailyLoopCalculator.build(
                events = emptyList(),
                routineChecks = checks,
                todos = emptyList(),
                diaries = diaries,
                today = today,
                steps = steps
            ),
            routines = listOf(
                RoutineEntity(id = 1, syncId = "r1", title = "물 한 컵"),
                RoutineEntity(id = 2, syncId = "r2", title = "저녁 산책")
            ),
            todayChecks = checks,
            diaries = diaries,
            items = emptyList(),
            includeDiaryBody = false,
            talk = talk
        )
    }
}
