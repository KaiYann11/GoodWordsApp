package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 어디에 물어볼지 고르는 자리입니다.
 *
 * 열쇠와 모델이 공급자마다 다릅니다. 한쪽 값을 다른 쪽으로 들고 가면 "모델이 없다"거나
 * "열쇠가 틀렸다"는 말만 돌아오고, 사용자는 무엇이 어긋났는지 알 수 없습니다.
 */
class AiFeedbackSettingsTest {
    @Test
    fun switchingProvidersKeepsBothKeys() {
        // 바꿔 보다가 앞서 넣어 둔 열쇠가 지워지면, 되돌리려고 열쇠를 다시 받아 와야 합니다.
        val settings = AiFeedbackSettings()
            .withKey("sk-openai")
            .withProvider(AiProvider.ANTHROPIC)
            .withKey("sk-ant-claude")

        assertEquals("sk-openai", settings.openAiKey)
        assertEquals("sk-ant-claude", settings.anthropicKey)
        assertEquals("sk-ant-claude", settings.activeKey)

        assertEquals("sk-openai", settings.withProvider(AiProvider.OPENAI).activeKey)
    }

    @Test
    fun theKeyOfTheOtherProviderDoesNotCountAsMine() {
        // Claude 열쇠만 넣고 OpenAI로 부르면 401만 돌아옵니다. 부르기 전에 막습니다.
        val settings = AiFeedbackSettings(anthropicKey = "sk-ant-claude")

        assertFalse(settings.canCallDirectly)
        assertTrue(settings.withProvider(AiProvider.ANTHROPIC).canCallDirectly)
    }

    @Test
    fun aModelFromTheOtherProviderIsPulledBackToItsDefault() {
        // gpt-6.1-sol을 Claude에 보내면 "모델을 찾을 수 없다"는 말만 옵니다.
        val settings = AiFeedbackSettings(provider = AiProvider.ANTHROPIC.name, model = "gpt-6.1-sol")

        assertEquals("claude-opus-5-5", settings.effectiveModel)
    }

    @Test
    fun anEmptyModelMeansTheDefaultOfWhicheverProviderIsChosen() {
        assertEquals("gpt-6-luna", AiFeedbackSettings().effectiveModel)
        assertEquals(
            "claude-opus-5-5",
            AiFeedbackSettings().withProvider(AiProvider.ANTHROPIC).effectiveModel
        )
    }

    @Test
    fun aChosenModelIsKept() {
        val settings = AiFeedbackSettings(provider = AiProvider.ANTHROPIC.name, model = "claude-haiku-5-5")

        assertEquals("claude-haiku-5-5", settings.effectiveModel)
    }

    @Test
    fun aModelThatLeftTheListFallsBackToTheDefaultInsteadOfFailing() {
        // 목록을 새 세대로 바꾸면 휴대폰에는 예전에 고른 이름이 그대로 남아 있습니다. 그 이름을 그대로 보내면
        // 공급자가 "그런 모델 없음"으로 거절해, 업데이트만 했는데 돌아보기와 오늘의 한마디가 멈춥니다.
        assertEquals("claude-opus-5-5", AiFeedbackSettings(provider = AiProvider.ANTHROPIC.name, model = "claude-opus-5").effectiveModel)
        assertEquals("gpt-6-luna", AiFeedbackSettings(provider = AiProvider.OPENAI.name, model = "gpt-4o-mini").effectiveModel)
        assertEquals(AiProvider.CLI_DEFAULT_MODEL, AiFeedbackSettings(provider = AiProvider.CLAUDE_CLI.name, model = "claude-haiku-4-5").effectiveModel)
        assertEquals(AiProvider.CLI_DEFAULT_MODEL, AiFeedbackSettings(provider = AiProvider.CODEX_CLI.name, model = "gpt-5.5").effectiveModel)
    }

    @Test
    fun anUnknownProviderIsReadAsOpenAi() {
        // 예전에 저장해 둔 설정이나 잘못 적힌 값이 와도 설정 전체를 버리지 않습니다.
        assertEquals(AiProvider.OPENAI, AiProvider.of(null))
        assertEquals(AiProvider.OPENAI, AiProvider.of("gemini"))
        assertEquals(AiProvider.ANTHROPIC, AiProvider.of("anthropic"))
        assertEquals(AiProvider.CLAUDE_CLI, AiProvider.of("claude_cli"))
    }

    @Test
    fun cliProvidersHaveNoKeyAndCannotBeCalledFromThisDevice() {
        // CLI는 서버에 깔려 있습니다. 이 기기에서 부를 길이 없는데 열쇠가 있다고 직접 부르면
        // 엉뚱한 곳(API)으로 나가거나 아무 데도 못 갑니다.
        val settings = AiFeedbackSettings(openAiKey = "sk-openai").withProvider(AiProvider.CLAUDE_CLI)

        assertTrue(settings.requiresServer)
        assertFalse(settings.canCallDirectly)
        assertEquals("", settings.activeKey)
        // 열쇠 칸이 없으니 넣어도 다른 공급자의 열쇠를 건드리지 않습니다.
        assertEquals("sk-openai", settings.withKey("whatever").openAiKey)
        assertEquals("sk-openai", settings.withProvider(AiProvider.OPENAI).activeKey)
    }

    @Test
    fun cliDefaultModelIsBlankAndTheReportNamesTheProvider() {
        // 빈 모델은 "서버의 CLI에 설정해 둔 모델"이라는 뜻입니다. 서버가 --model을 붙이지 않습니다.
        val settings = AiFeedbackSettings().withProvider(AiProvider.CODEX_CLI)

        assertEquals(AiProvider.CLI_DEFAULT_MODEL, settings.effectiveModel)
        // 그래도 돌아보기에는 누가 썼는지 남아야 합니다.
        assertEquals("Codex CLI", settings.reportModel)
        assertEquals("gpt-6.1-sol", settings.copy(model = "gpt-6.1-sol").reportModel)
        // 목록에 없는 이름을 들고 오면 CLI 기본으로 되돌립니다.
        assertEquals(AiProvider.CLI_DEFAULT_MODEL, settings.copy(model = "gpt-4o").effectiveModel)
    }

    @Test
    fun onlyServerSideProvidersOfferTheBlankDefaultModel() {
        // API 공급자에 빈 모델을 보내면 "모델이 없다"는 400만 돌아옵니다.
        AiProvider.entries.forEach { provider ->
            assertEquals(provider.name, provider.serverOnly, provider.models.contains(AiProvider.CLI_DEFAULT_MODEL))
        }
    }

    @Test
    fun ollamaIsAskedThroughTheServerWithoutAKey() {
        // Ollama는 서버 PC에서 돕니다. 이 기기에 열쇠가 있다고 직접 부르면 엉뚱한 곳(API)으로 나갑니다.
        val settings = AiFeedbackSettings(openAiKey = "sk-openai").withProvider(AiProvider.OLLAMA)

        assertEquals(AiProvider.OLLAMA, AiProvider.of("ollama"))
        assertTrue(settings.requiresServer)
        assertFalse(settings.canCallDirectly)
        assertFalse(settings.canAsk(hasServer = false))
        assertTrue(settings.canAsk(hasServer = true))
        assertEquals("", settings.activeKey)
        assertEquals("sk-openai", settings.withKey("whatever").openAiKey)
    }

    @Test
    fun ollamaLeavesTheModelToTheServer() {
        // 서버 PC에 무엇을 받아 두었는지 이 기기는 모릅니다. 다른 공급자의 모델을 들고 가면 없는 모델이 됩니다.
        val settings = AiFeedbackSettings(model = "gpt-4o").withProvider(AiProvider.OLLAMA)

        assertEquals(AiProvider.CLI_DEFAULT_MODEL, settings.effectiveModel)
        assertEquals(AiProvider.CLI_DEFAULT_MODEL, settings.copy(model = "gpt-4o").effectiveModel)
        assertEquals("Ollama", settings.reportModel)
        // CLI가 아니니 "CLI 기본"이라 적지 않습니다.
        assertEquals("서버 기본", AiProvider.OLLAMA.modelLabel(AiProvider.CLI_DEFAULT_MODEL))
        assertEquals("CLI 기본", AiProvider.CODEX_CLI.modelLabel(AiProvider.CLI_DEFAULT_MODEL))
    }

    @Test
    fun everyProviderHasAtLeastOneModel() {
        // 모델이 없으면 defaultModel이 터집니다.
        AiProvider.entries.forEach { provider ->
            assertTrue(provider.name, provider.models.isNotEmpty())
        }
    }

    @Test
    fun canAskFollowsTheSameRouteTheClientTakes() {
        // 화면이 "받을 수 있다"고 해 놓고 부를 때마다 실패하거나, 부를 수 있는데 조용히
        // 아무것도 안 하면 사용자는 무엇이 어긋났는지 알 수 없습니다.
        val none = AiFeedbackSettings()
        assertFalse(none.canAsk(hasServer = false))
        assertTrue("서버가 열쇠를 들고 있을 수 있습니다.", none.canAsk(hasServer = true))

        val withKey = AiFeedbackSettings(openAiKey = "sk-openai")
        assertTrue(withKey.canAsk(hasServer = false))

        // CLI는 이 기기에 없습니다. 서버가 없으면 되돌아갈 길이 없습니다.
        val cli = AiFeedbackSettings(openAiKey = "sk-openai").withProvider(AiProvider.CLAUDE_CLI)
        assertFalse(cli.canAsk(hasServer = false))
        assertTrue(cli.canAsk(hasServer = true))
    }

    @Test
    fun howOftenToAskIsUpToTheUser() {
        val settings = AiFeedbackSettings(coachAskLimit = 1, coachGapMinutes = 30)

        assertEquals(1, settings.askLimit)
        assertEquals(30L, settings.gapMinutes)
        assertFalse(settings.asksOnlyWhenAsked)
    }

    @Test
    fun theGapHasAFloorAndTheCountHasACeiling() {
        // 0분이면 오늘 한 것이 바뀔 때마다 묻게 되어, 루틴을 연달아 밟은 날 그 횟수가
        // 그대로 요금이 됩니다. 천장이 없으면 설정 한 번으로 하루 요금이 끝없이 커집니다.
        assertEquals(AiFeedbackSettings.MIN_GAP_MINUTES.toLong(), AiFeedbackSettings(coachGapMinutes = 0).gapMinutes)
        assertEquals(AiFeedbackSettings.MAX_ASK_LIMIT, AiFeedbackSettings(coachAskLimit = 9999).askLimit)
        assertEquals(0, AiFeedbackSettings(coachAskLimit = -3).askLimit)
    }

    @Test
    fun theGapRunsFromTenMinutesToADay() {
        // 사용자가 정한 범위입니다. 하루보다 길면 자정에 비우는 한마디가 영영 오지 않습니다.
        assertEquals(10L, AiFeedbackSettings(coachGapMinutes = 3).gapMinutes)
        assertEquals(10L, AiFeedbackSettings(coachGapMinutes = 10).gapMinutes)
        assertEquals(1440L, AiFeedbackSettings(coachGapMinutes = 5000).gapMinutes)
        assertEquals(10, AiFeedbackSettings.GAP_MINUTE_CHOICES.first())
        assertEquals(1440, AiFeedbackSettings.GAP_MINUTE_CHOICES.last())
    }

    @Test
    fun theBackgroundPathRunsOnlyWhenItCanAsk() {
        // 앱을 닫아도 도는 길은 값이 드는 일이라 켜 둔 사람에게만, 저절로 묻게 해 둔 때에만 돕니다.
        val on = AiFeedbackSettings(coachEnabled = true)
        assertTrue(on.coachRunsInBackground)
        assertFalse(on.copy(coachBackground = false).coachRunsInBackground)
        assertFalse(on.copy(coachEnabled = false).coachRunsInBackground)
        // 하루 횟수를 "안 함"으로 두면 저절로 묻지 않으니 깨울 일도 없습니다.
        assertFalse(on.copy(coachAskLimit = 0).coachRunsInBackground)
    }

    @Test
    fun askingNoneStillLeavesTheButton() {
        // "안 함"은 저절로 묻지 않는다는 뜻입니다. 직접 누르는 것까지 막지 않습니다.
        assertTrue(AiFeedbackSettings(coachAskLimit = 0).asksOnlyWhenAsked)
    }

    @Test
    fun theFreeLineIsOnByDefault() {
        // 아직 안 밟은 루틴을 알려 주는 쪽은 AI를 부르지 않아 값이 들지 않습니다.
        assertTrue(AiFeedbackSettings().coachNudgeEnabled)
    }

    @Test
    fun theChoicesOnScreenAreWithinWhatIsAllowed() {
        AiFeedbackSettings.ASK_LIMIT_CHOICES.forEach { limit ->
            assertEquals(limit, AiFeedbackSettings(coachAskLimit = limit).askLimit)
        }
        AiFeedbackSettings.GAP_MINUTE_CHOICES.forEach { minutes ->
            assertEquals(minutes.toLong(), AiFeedbackSettings(coachGapMinutes = minutes).gapMinutes)
        }
    }

    @Test
    fun theDailyWordIsOffUntilItIsTurnedOn() {
        // 하루에 여러 번 값이 드는 길이라 기본으로 켜 두지 않습니다.
        assertFalse(AiFeedbackSettings().coachEnabled)
        // 돌아보기 주기와 따로 켭니다.
        assertFalse(AiFeedbackSettings(schedule = ReportPeriod.WEEKLY.name).coachEnabled)
        assertTrue(AiFeedbackSettings(coachEnabled = true).coachEnabled)
    }
}
