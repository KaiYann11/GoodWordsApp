package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 계열과 세대를 이름에서 읽는 규칙.
 *
 * "Sonnet 최신"이 다음 세대로 넘어가는 것은 이 셈 하나에 달려 있습니다. 틀리면 새 모델이 나와도 옛 모델에 머물거나, 엉뚱한
 * 계열(값이 몇 배인 Fable)로 넘어갑니다.
 */
class AiModelsTest {
    @Test
    fun theFamilyIsTheNameWithoutTheGeneration() {
        assertEquals("opus", AiModels.familyOf("claude-opus-5-5"))
        assertEquals("fable", AiModels.familyOf("claude-fable-5"))
        assertEquals("haiku", AiModels.familyOf("claude-haiku-4-5-20251001"))
        // 3.x 때의 꼴도 같은 계열입니다.
        assertEquals("sonnet", AiModels.familyOf("claude-3-5-sonnet-20241022"))
        assertEquals("terra", AiModels.familyOf("gpt-6.1-terra"))
        assertEquals("luna", AiModels.familyOf("gpt-6-luna"))
        // 꼴이 다른 이름은 계열이 없습니다 — "다른 모델…"에서만 고릅니다.
        assertNull(AiModels.familyOf("claude-mythos-preview"))
        assertNull(AiModels.familyOf("gpt-4o-mini"))
        assertNull(AiModels.familyOf("gpt-6-luna-2026-10-15"))
        assertNull(AiModels.familyOf("qwen3.6:27b"))
        assertNull(AiModels.familyOf("sonnet"))
    }

    @Test
    fun theGenerationIgnoresTheDateTail() {
        assertEquals(listOf(5, 5), AiModels.versionOf("claude-opus-5-5"))
        assertEquals(listOf(4, 5), AiModels.versionOf("claude-haiku-4-5-20251001"))
        assertEquals(listOf(6, 1), AiModels.versionOf("gpt-6.1-terra"))
    }

    @Test
    fun theNewestIsCountedByGenerationNotByListOrder() {
        // 목록의 차례는 공급자마다 다릅니다(Codex는 보여 주는 차례, OpenAI는 뒤죽박죽).
        val models = listOf(
            AiModelInfo("claude-opus-4-8"),
            AiModelInfo("claude-opus-5"),
            AiModelInfo("claude-opus-5-5"),
            AiModelInfo("claude-sonnet-9")
        )

        assertEquals("claude-opus-5-5", AiModels.newestIn("opus", models)?.id)
        assertEquals("claude-sonnet-9", AiModels.newestIn("sonnet", models)?.id)
        assertNull(AiModels.newestIn("haiku", models))
        assertEquals(
            "gpt-6.1-sol",
            AiModels.newestIn("sol", listOf(AiModelInfo("gpt-5.6-sol"), AiModelInfo("gpt-6.1-sol"), AiModelInfo("gpt-6-sol")))?.id
        )
    }

    @Test
    fun aDatedSnapshotLosesToTheNameThatAlwaysPointsAtTheNewest() {
        val models = listOf(AiModelInfo("claude-haiku-4-5-20251001"), AiModelInfo("claude-haiku-4-5"))

        assertEquals("claude-haiku-4-5", AiModels.newestIn("haiku", models)?.id)
    }

    @Test
    fun familiesKeepTheProvidersOrderAndAddNewOnesAfter() {
        // 받아 온 목록은 새로 나온 것부터라 Fable이 앞에 옵니다. 칸은 공급자가 정한 차례(Opus가 기본)로 둡니다.
        val fetched = listOf(
            AiModelInfo("claude-fable-5-2"),
            AiModelInfo("claude-opus-5-5"),
            AiModelInfo("claude-sonnet-5-5"),
            AiModelInfo("claude-haiku-5-5"),
            AiModelInfo("claude-nova-5")
        )

        assertEquals(
            listOf("opus", "sonnet", "haiku", "fable", "nova"),
            AiModels.families(fetched, AiProvider.ANTHROPIC.familyChoices)
        )
    }

    @Test
    fun familiesTwoGenerationsBehindAreLeftToTheOtherModelsList() {
        // OpenAI 목록에는 몇 세대 전 계열이 남아 있습니다. 다 칸으로 두면 끝없이 늘어납니다.
        val fetched = listOf(
            AiModelInfo("gpt-6.1-terra"),
            AiModelInfo("gpt-6-luna"),
            AiModelInfo("gpt-5-mini"),
            AiModelInfo("gpt-4.1-nano")
        )

        assertEquals(listOf("luna", "terra", "mini"), AiModels.families(fetched, AiProvider.OPENAI.familyChoices))
        // 새 세대가 한 계열만 먼저 나와도 다른 계열 칸이 사라지지 않습니다(Opus 6이 먼저 나와도 Sonnet 5.5는 지금 것).
        assertEquals(
            listOf("opus", "sonnet"),
            AiModels.families(listOf(AiModelInfo("claude-opus-6"), AiModelInfo("claude-sonnet-5-5")), AiProvider.ANTHROPIC.familyChoices)
        )
        assertEquals(emptyList<String>(), AiModels.families(emptyList(), AiProvider.ANTHROPIC.familyChoices))
    }

    @Test
    fun onlyTextModelsComeFromTheOpenAiList() {
        assertTrue(AiModels.isOpenAiChatModel("gpt-6-luna"))
        assertTrue(AiModels.isOpenAiChatModel("o4-mini"))
        assertFalse(AiModels.isOpenAiChatModel("gpt-image-1"))
        assertFalse(AiModels.isOpenAiChatModel("text-embedding-3-large"))
        assertFalse(AiModels.isOpenAiChatModel("gpt-4o-realtime-preview"))
        assertFalse(AiModels.isOpenAiChatModel("dall-e-3"))
    }

    @Test
    fun aTypedNameMustBeSafeToHandToTheServer() {
        assertTrue(AiModels.isValidName("claude-fable-5-2"))
        assertTrue(AiModels.isValidName("qwen3.6:27b"))
        assertTrue(AiModels.isValidName("library/llama3:8b"))
        assertTrue(AiModels.isValidName("fable"))
        assertFalse(AiModels.isValidName(""))
        assertFalse(AiModels.isValidName("opus\" && echo"))
        assertFalse(AiModels.isValidName("two words"))
        assertFalse(AiModels.isValidName("-flag"))
    }

    @Test
    fun aChoiceNamesItsFamilyOnlyWhenItIsALatestChoice() {
        assertEquals("sonnet", AiModels.familyOfChoice(AiModels.latest("sonnet")))
        assertNull(AiModels.familyOfChoice("claude-sonnet-5-5"))
        assertNull(AiModels.familyOfChoice(""))
        assertNull(AiModels.familyOfChoice("latest:"))
        assertEquals("Fable 최신", AiModels.familyLabel("fable"))
    }
}
