package com.codex.appgoodwords.ui.screen

import com.codex.appgoodwords.data.AiFeedbackSettings
import com.codex.appgoodwords.data.AiLastAnswer
import com.codex.appgoodwords.data.AiModelInfo
import com.codex.appgoodwords.data.AiModelList
import com.codex.appgoodwords.data.AiModels
import com.codex.appgoodwords.data.AiProvider
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** 모델 칸 아래 "지금 답하는 모델" 줄과, "다른 모델…" 창 맨 위의 목록 출처 줄. */
class AiModelPickerTextTest {
    private val sonnet = AiFeedbackSettings(provider = AiProvider.ANTHROPIC.name, model = AiModels.latest("sonnet"))

    @Test
    fun aFamilyChoiceNamesTheModelItResolvesToAndThatItWillMoveOn() {
        val fetched = AiModelList(listOf(AiModelInfo("claude-sonnet-6"), AiModelInfo("claude-sonnet-5-5")), fetchedAt = 1L)

        assertEquals(
            "지금 답하는 모델: claude-sonnet-5-5\nSonnet 다음 세대가 나오면 저절로 바뀝니다.",
            nowLine(sonnet, AiModelList(), lastAnswer = null)
        )
        assertEquals(
            "지금 답하는 모델: claude-sonnet-6\nSonnet 다음 세대가 나오면 저절로 바뀝니다.",
            nowLine(sonnet, fetched, lastAnswer = null)
        )
    }

    @Test
    fun claudeCliShowsWhatAnsweredLastTimeBecauseTheCliPicks() {
        val cli = AiFeedbackSettings(provider = AiProvider.CLAUDE_CLI.name, model = AiModels.latest("sonnet"))

        assertEquals(
            "서버의 Claude CLI가 가장 새 Sonnet 모델로 답합니다.\nSonnet 다음 세대가 나오면 저절로 바뀝니다.",
            nowLine(cli, AiModelList(), lastAnswer = null)
        )
        assertEquals(
            "지금 답하는 모델: claude-sonnet-5-5\nSonnet 다음 세대가 나오면 저절로 바뀝니다.",
            nowLine(cli, AiModelList(), AiLastAnswer(AiProvider.CLAUDE_CLI, AiModels.latest("sonnet"), "claude-sonnet-5-5"))
        )
        // 다른 것을 골랐을 때 답한 이름은 지금 고른 것의 이름이 아닙니다.
        assertEquals(
            "서버의 Claude CLI가 가장 새 Sonnet 모델로 답합니다.\nSonnet 다음 세대가 나오면 저절로 바뀝니다.",
            nowLine(cli, AiModelList(), AiLastAnswer(AiProvider.CLAUDE_CLI, AiModels.latest("opus"), "claude-opus-5-5"))
        )
    }

    @Test
    fun theServerDefaultAndAPinnedNameSayWhoPicks() {
        val ollama = AiFeedbackSettings().withProvider(AiProvider.OLLAMA)
        val codex = AiFeedbackSettings().withProvider(AiProvider.CODEX_CLI)

        assertEquals("서버에 받아 둔 모델 가운데 가장 최근에 받은 것이 답합니다.", nowLine(ollama, AiModelList(), null))
        assertEquals("서버의 Codex CLI에 정해 둔 모델이 답합니다.", nowLine(codex, AiModelList(), null))
        assertEquals(
            "지금 답하는 모델: gpt-6.1-terra",
            nowLine(codex, AiModelList(), AiLastAnswer(AiProvider.CODEX_CLI, "", "gpt-6.1-terra"))
        )
        assertEquals(
            "이 이름으로 묻습니다. 새 모델이 나와도 바뀌지 않습니다.",
            nowLine(sonnet.withModel("claude-sonnet-5-5"), AiModelList(), null)
        )
    }

    @Test
    fun theSourceLineSaysWhereAndWhenTheListCameFrom() {
        val zone = ZoneId.of("Asia/Seoul")
        val at = Instant.parse("2026-11-25T05:02:00Z").toEpochMilli()

        assertEquals("아직 목록을 받지 못해 앱에 든 이름입니다.", sourceLine(AiProvider.ANTHROPIC, AiModelList(), zone))
        assertEquals(
            "Claude 목록 · 11월 25일 14:02 받음",
            sourceLine(AiProvider.ANTHROPIC, AiModelList(listOf(AiModelInfo("claude-opus-5-5")), at), zone)
        )
        assertEquals(
            "서버의 Codex 목록 · 11월 25일 14:02 받음",
            sourceLine(AiProvider.CODEX_CLI, AiModelList(listOf(AiModelInfo("gpt-6.1-terra")), at), zone)
        )
        assertEquals("서버의 Ollama 목록이 비어 있습니다 · 11월 25일 14:02 받음", sourceLine(AiProvider.OLLAMA, AiModelList(emptyList(), at), zone))
        assertEquals("Claude CLI는 목록을 알려 주지 않아 앱에 든 이름입니다.", sourceLine(AiProvider.CLAUDE_CLI, AiModelList(), zone))
    }
}
