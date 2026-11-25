package com.codex.appgoodwords.ui.screen

import com.codex.appgoodwords.data.AiFeedbackSettings
import com.codex.appgoodwords.data.AiModelCheck
import com.codex.appgoodwords.data.AiModelInfo
import com.codex.appgoodwords.data.AiModelList
import com.codex.appgoodwords.data.AiModels
import com.codex.appgoodwords.data.AiProvider
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** 모델 칸 아래 "지금 답하는 모델" 줄, "모델 정보 받아오기" 아래 칸별 줄, "다른 모델…" 창 맨 위의 목록 출처 줄. */
class AiModelPickerTextTest {
    private val sonnet = AiFeedbackSettings(provider = AiProvider.ANTHROPIC.name, model = AiModels.latest("sonnet"))

    @Test
    fun aFamilyChoiceNamesTheModelItResolvesToAndThatItWillMoveOn() {
        val fetched = AiModelList(listOf(AiModelInfo("claude-sonnet-6"), AiModelInfo("claude-sonnet-5-5")), fetchedAt = 1L)

        assertEquals(
            "지금 답하는 모델: claude-sonnet-5-5\nSonnet 다음 세대가 나오면 저절로 바뀝니다.",
            nowLine(sonnet, AiModelList(), AiModelCheck())
        )
        assertEquals(
            "지금 답하는 모델: claude-sonnet-6\nSonnet 다음 세대가 나오면 저절로 바뀝니다.",
            nowLine(sonnet, fetched, AiModelCheck())
        )
        // 열쇠로 부르는 칸은 받아 온 목록이 정합니다. 예전에 답한 이름이 남아 있어도 지금 보낼 이름을 적습니다.
        assertEquals(
            "지금 답하는 모델: claude-sonnet-6\nSonnet 다음 세대가 나오면 저절로 바뀝니다.",
            nowLine(sonnet, fetched, AiModelCheck(names = mapOf(AiModels.latest("sonnet") to "claude-sonnet-5-5")))
        )
    }

    @Test
    fun claudeCliShowsTheNameItsChoiceWasLastKnownBy() {
        val cli = AiFeedbackSettings(provider = AiProvider.CLAUDE_CLI.name, model = AiModels.latest("sonnet"))

        assertEquals(
            "서버의 Claude CLI가 가장 새 Sonnet 모델로 답합니다.\nSonnet 다음 세대가 나오면 저절로 바뀝니다.",
            nowLine(cli, AiModelList(), AiModelCheck())
        )
        assertEquals(
            "지금 답하는 모델: claude-sonnet-5-5\nSonnet 다음 세대가 나오면 저절로 바뀝니다.",
            nowLine(cli, AiModelList(), AiModelCheck(names = mapOf(AiModels.latest("sonnet") to "claude-sonnet-5-5")))
        )
        // 다른 칸의 이름은 지금 고른 칸의 이름이 아닙니다.
        assertEquals(
            "서버의 Claude CLI가 가장 새 Sonnet 모델로 답합니다.\nSonnet 다음 세대가 나오면 저절로 바뀝니다.",
            nowLine(cli, AiModelList(), AiModelCheck(names = mapOf(AiModels.latest("opus") to "claude-opus-5-5")))
        )
        // 적어 둔 별칭은 CLI가 그 계열의 가장 새 모델로 바꿉니다 — "바뀌지 않습니다"가 아닙니다.
        assertEquals(
            "서버의 Claude CLI가 이 별칭의 모델로 답합니다.\n별칭이라 새 모델이 나오면 Claude CLI가 바꿉니다.",
            nowLine(cli.withModel("fable"), AiModelList(), AiModelCheck())
        )
        assertEquals(
            "지금 답하는 모델: claude-fable-5-2\n별칭이라 새 모델이 나오면 Claude CLI가 바꿉니다.",
            nowLine(cli.withModel("fable"), AiModelList(), AiModelCheck(names = mapOf("fable" to "claude-fable-5-2")))
        )
    }

    @Test
    fun theServerDefaultAndAPinnedNameSayWhoPicks() {
        val ollama = AiFeedbackSettings().withProvider(AiProvider.OLLAMA)
        val codex = AiFeedbackSettings().withProvider(AiProvider.CODEX_CLI)

        assertEquals("서버에 받아 둔 모델 가운데 가장 최근에 받은 것이 답합니다.", nowLine(ollama, AiModelList(), AiModelCheck()))
        assertEquals("서버의 Codex CLI에 정해 둔 모델이 답합니다.", nowLine(codex, AiModelList(), AiModelCheck()))
        assertEquals(
            "지금 답하는 모델: gpt-6.1-terra",
            nowLine(codex, AiModelList(), AiModelCheck(names = mapOf("" to "gpt-6.1-terra")))
        )
        assertEquals(
            "지금 답하는 모델: qwen3.6:27b",
            nowLine(ollama, AiModelList(), AiModelCheck(names = mapOf("" to "qwen3.6:27b")))
        )
        assertEquals(
            "이 이름으로 묻습니다. 새 모델이 나와도 바뀌지 않습니다.",
            nowLine(sonnet.withModel("claude-sonnet-5-5"), AiModelList(), AiModelCheck())
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

    @Test
    fun theCheckRowsFollowTheChipsAndSayWhatIsUnknown() {
        val cli = AiFeedbackSettings(provider = AiProvider.CLAUDE_CLI.name, model = AiModels.latest("sonnet"))
        val check = AiModelCheck(
            checkedAt = 1L,
            names = mapOf(
                "" to "claude-opus-5-5",
                AiModels.latest("opus") to "claude-opus-5-5",
                AiModels.latest("sonnet") to "claude-sonnet-5-5",
                // 지난번에는 알았지만 이번에는 알아내지 못했습니다. 지난 이름을 지금 것처럼 적지 않습니다.
                AiModels.latest("haiku") to "claude-haiku-5"
            ),
            errors = mapOf(AiModels.latest("haiku") to "사용 한도에 걸렸습니다")
        )

        val rows = checkRows(cli, emptyList(), AiProvider.CLAUDE_CLI.familyChoices, check)

        assertEquals(listOf("CLI 기본", "Opus 최신", "Sonnet 최신", "Haiku 최신"), rows.map { it.label })
        assertEquals(listOf("claude-opus-5-5", "claude-opus-5-5", "claude-sonnet-5-5", ""), rows.map { it.model })
        assertEquals("알아내지 못함: 사용 한도에 걸렸습니다", rows.last().text)

        // 적어 둔 별칭도 한 줄입니다. 아직 모르면 그렇게 적습니다.
        val typed = checkRows(cli.withModel("fable"), emptyList(), AiProvider.CLAUDE_CLI.familyChoices, check)
        assertEquals("fable", typed.last().choice)
        assertEquals("아직 모릅니다. 다시 받아 보세요.", typed.last().text)
    }

    @Test
    fun codexRowsNameItsDefaultFromTheServerAndItsFamiliesFromTheList() {
        val codex = AiFeedbackSettings(provider = AiProvider.CODEX_CLI.name, model = AiModels.latest("sol"))
        val models = listOf(AiModelInfo("gpt-6.1-terra"), AiModelInfo("gpt-6.1-sol"), AiModelInfo("gpt-6-sol"), AiModelInfo("gpt-6-luna"))
        val families = AiModels.families(models, AiProvider.CODEX_CLI.familyChoices)
        // 목록 칸에 남은 예전 이름은 쓰지 않습니다 — 지금 보낼 이름은 목록이 정합니다.
        val check = AiModelCheck(checkedAt = 1L, names = mapOf("" to "gpt-6.1-terra", AiModels.latest("terra") to "gpt-6-terra"))

        val rows = checkRows(codex, models, families, check)

        assertEquals(listOf("", "latest:terra", "latest:sol", "latest:luna"), rows.map { it.choice })
        assertEquals(listOf("gpt-6.1-terra", "gpt-6.1-terra", "gpt-6.1-sol", "gpt-6-luna"), rows.map { it.model })

        // 열쇠로 부르는 곳에는 CLI 기본 줄이 없습니다.
        val claude = AiFeedbackSettings(provider = AiProvider.ANTHROPIC.name)
        val claudeModels = listOf(AiModelInfo("claude-opus-5-5"), AiModelInfo("claude-sonnet-5-5"))
        val claudeRows = checkRows(claude, claudeModels, AiModels.families(claudeModels, AiProvider.ANTHROPIC.familyChoices), AiModelCheck(1L))
        assertEquals(listOf("Opus 최신" to "claude-opus-5-5", "Sonnet 최신" to "claude-sonnet-5-5"), claudeRows.map { it.label to it.model })
    }

    @Test
    fun theCheckedLineSaysWhenTheButtonWasPressed() {
        val at = Instant.parse("2026-11-25T06:30:00Z").toEpochMilli()

        assertEquals("11월 25일 15:30에 받음", checkedLine(at, ZoneId.of("Asia/Seoul")))
    }
}
