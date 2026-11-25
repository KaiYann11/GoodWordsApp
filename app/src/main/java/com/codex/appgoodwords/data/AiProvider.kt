package com.codex.appgoodwords.data

/** 열거 항목을 만들 때는 동반 객체가 아직 없어, 같은 값을 파일 자리에 둡니다. */
private const val CLI_DEFAULT = ""

/**
 * AI가 권한 것을 담을 때 붙는 갈래.
 *
 * 돌아보기의 추천 글귀·추천 루틴과 오늘의 한마디가 **같은 이름을 씁니다.** 두 곳에 따로
 * 적어 두면 한쪽만 고쳐졌을 때 보관함에서 "AI 추천"이 두 갈래로 갈라집니다.
 */
const val AI_CATEGORY = "AI 추천"

/**
 * 돌아보기를 써 줄 AI.
 *
 * 부르는 길이 둘입니다. 열쇠로 API를 부르는 길과, 서버가 대신 묻는 길([serverOnly])입니다.
 * 서버가 묻는 쪽은 서버에 로그인해 둔 CLI(Claude Code·Codex)이거나 서버 PC에서 도는 Ollama라
 * 열쇠가 없습니다. 대신 서버 없이는 부를 수 없습니다 — 이 기기에는 둘 다 없습니다.
 *
 * 열쇠는 공급자마다 따로 둡니다. 하나만 두면 다른 쪽으로 바꿔 볼 때 넣어 둔 열쇠가 지워져,
 * 되돌리려면 다시 넣어야 합니다.
 *
 * **모델 이름은 여기 박아 두지 않습니다**(사용자가 정함, 2026-11-25). 고르는 것은 계열("Sonnet 최신")이고, 그 계열의 가장 새
 * 모델은 공급자·서버에서 받아 온 목록에서 셉니다([AiModels]). [builtInModels]는 목록을 받지 못했을 때만 씁니다 — 낡아도
 * 받아 온 목록이 있으면 보이지 않습니다.
 */
enum class AiProvider(
    val label: String,
    /** 열쇠 칸에 적어 줄 힌트. 잘못된 열쇠를 넣고 왜 안 되는지 모르는 일을 줄입니다. 서버 쪽에는 없습니다. */
    val keyHint: String,
    /** 목록을 받지 못했을 때 쓰는 모델(2026-11-25에 본 것). "다른 모델…"에서 고를 거리로도 보입니다. */
    val builtInModels: List<AiModelInfo>,
    /** 칸으로 먼저 보일 계열의 차례. 받아 온 목록에 새 계열이 있으면 그 뒤에 붙습니다([AiModels.families]). */
    val familyChoices: List<String>,
    /** 빈 값(기본)이 가리키는 계열. CLI·Ollama의 기본은 서버 쪽이 정해 없습니다. */
    val defaultFamily: String? = null,
    /**
     * 계열 이름을 그대로 넘기면 받는 쪽이 가장 새 것을 고르는지. Claude CLI는 `--model sonnet`을 가장 새 Sonnet으로 읽습니다
     * (2.1.331에서 확인). 목록을 받을 길이 없어서이기도 합니다.
     */
    val familyAliases: Boolean = false,
    /** 서버가 대신 묻는지(CLI·Ollama). 열쇠 대신 서버 주소가 있어야 합니다. */
    val serverOnly: Boolean = false,
    /** 빈 모델을 화면에 적을 이름. 누가 모델을 정하는지가 공급자마다 다릅니다. */
    private val blankModelLabel: String = "CLI 기본"
) {
    OPENAI(
        label = "OpenAI",
        keyHint = "sk-로 시작합니다",
        builtInModels = listOf(
            AiModelInfo("gpt-6.1-terra"),
            AiModelInfo("gpt-6.1-sol"),
            AiModelInfo("gpt-6-astra"),
            AiModelInfo("gpt-6-sol"),
            AiModelInfo("gpt-6-luna")
        ),
        familyChoices = listOf("luna", "terra", "sol", "astra"),
        // 예전 기본(`gpt-4o-mini`)처럼 가장 값싼 급입니다.
        defaultFamily = "luna"
    ),
    ANTHROPIC(
        label = "Claude",
        keyHint = "sk-ant-로 시작합니다",
        builtInModels = listOf(
            AiModelInfo("claude-fable-5-2", "Claude Fable 5.2"),
            AiModelInfo("claude-opus-5-5", "Claude Opus 5.5"),
            AiModelInfo("claude-sonnet-5-5", "Claude Sonnet 5.5"),
            AiModelInfo("claude-haiku-5-5", "Claude Haiku 5.5")
        ),
        // 기록을 읽고 맥락을 짚어 주는 일이라 Opus가 기본입니다. 그 위 급인 Fable은 값이 높아 고를 수 있게만 둡니다.
        familyChoices = listOf("opus", "sonnet", "haiku", "fable"),
        defaultFamily = "opus"
    ),
    CLAUDE_CLI(
        label = "Claude CLI",
        keyHint = "",
        builtInModels = listOf(
            AiModelInfo("claude-opus-5-5", "Claude Opus 5.5"),
            AiModelInfo("claude-sonnet-5-5", "Claude Sonnet 5.5"),
            AiModelInfo("claude-haiku-5-5", "Claude Haiku 5.5")
        ),
        // Fable은 구독에서 사용 크레딧을 따로 켜야 돌고, 그 전에는 CLI가 거절합니다(2026-10-29). 그래서 칸에는 두지 않습니다 —
        // 켜 두었으면 "다른 모델…"에서 `fable`을 적으면 됩니다.
        familyChoices = listOf("opus", "sonnet", "haiku"),
        familyAliases = true,
        serverOnly = true
    ),
    CODEX_CLI(
        label = "Codex CLI",
        keyHint = "",
        // Codex가 보여 주는 차례(`~/.codex/models_cache.json`, 2026-11-25).
        builtInModels = listOf(
            AiModelInfo("gpt-6.1-terra"),
            AiModelInfo("gpt-6.1-sol"),
            AiModelInfo("gpt-6-astra"),
            AiModelInfo("gpt-6-sol"),
            AiModelInfo("gpt-6-luna")
        ),
        familyChoices = listOf("terra", "sol", "astra", "luna"),
        serverOnly = true
    ),
    OLLAMA(
        label = "Ollama",
        keyHint = "",
        // 서버 PC에 어떤 모델을 받아 두었는지 이 기기는 모릅니다. 빈 값이면 서버가 고릅니다
        // (`APP_GOOD_WORDS_OLLAMA_MODEL`, 없으면 가장 최근에 받은 모델). 받아 둔 목록은 서버가 알려 줍니다.
        builtInModels = emptyList(),
        familyChoices = emptyList(),
        serverOnly = true,
        blankModelLabel = "서버 기본"
    );

    /** 화면에 적을 모델 이름. 빈 값은 서버 쪽(CLI·Ollama)이 정한 기본, `latest:`는 그 계열의 최신입니다. */
    fun modelLabel(model: String): String =
        AiModels.familyOfChoice(model)?.let(AiModels::familyLabel) ?: model.ifBlank { blankModelLabel }

    companion object {
        /** 서버 쪽 공급자의 기본 모델. 서버에 빈 값으로 보내면 CLI는 `--model`을 붙이지 않고, Ollama는 서버가 고릅니다. */
        const val CLI_DEFAULT_MODEL = CLI_DEFAULT

        /** 모르는 이름이 와도 버리지 않고 OpenAI로 읽습니다. 예전에 저장해 둔 설정이 살아 있어야 합니다. */
        fun of(name: String?): AiProvider =
            entries.firstOrNull { it.name == name?.trim()?.uppercase() } ?: OPENAI
    }
}
