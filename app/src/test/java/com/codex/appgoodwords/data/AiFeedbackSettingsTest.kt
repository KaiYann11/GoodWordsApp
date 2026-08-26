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
        // gpt-4o를 Claude에 보내면 "모델을 찾을 수 없다"는 말만 옵니다.
        val settings = AiFeedbackSettings(provider = AiProvider.ANTHROPIC.name, model = "gpt-4o")

        assertEquals("claude-opus-5", settings.effectiveModel)
    }

    @Test
    fun anEmptyModelMeansTheDefaultOfWhicheverProviderIsChosen() {
        assertEquals("gpt-4o-mini", AiFeedbackSettings().effectiveModel)
        assertEquals(
            "claude-opus-5",
            AiFeedbackSettings().withProvider(AiProvider.ANTHROPIC).effectiveModel
        )
    }

    @Test
    fun aChosenModelIsKept() {
        val settings = AiFeedbackSettings(provider = AiProvider.ANTHROPIC.name, model = "claude-haiku-4-5")

        assertEquals("claude-haiku-4-5", settings.effectiveModel)
    }

    @Test
    fun anUnknownProviderIsReadAsOpenAi() {
        // 예전에 저장해 둔 설정이나 잘못 적힌 값이 와도 설정 전체를 버리지 않습니다.
        assertEquals(AiProvider.OPENAI, AiProvider.of(null))
        assertEquals(AiProvider.OPENAI, AiProvider.of("gemini"))
        assertEquals(AiProvider.ANTHROPIC, AiProvider.of("anthropic"))
    }

    @Test
    fun everyProviderHasAtLeastOneModel() {
        // 모델이 없으면 defaultModel이 터집니다.
        AiProvider.entries.forEach { provider ->
            assertTrue(provider.name, provider.models.isNotEmpty())
        }
    }
}
