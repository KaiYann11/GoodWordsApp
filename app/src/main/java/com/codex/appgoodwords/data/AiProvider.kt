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
 */
enum class AiProvider(
    val label: String,
    /** 열쇠 칸에 적어 줄 힌트. 잘못된 열쇠를 넣고 왜 안 되는지 모르는 일을 줄입니다. 서버 쪽에는 없습니다. */
    val keyHint: String,
    /**
     * 고를 수 있는 모델. 빈 문자열은 "서버 쪽이 정한 기본 모델"이라는 뜻이라 [serverOnly] 공급자에만
     * 있습니다. CLI는 `--model` 없이 돌아 CLI에 설정해 둔 모델이, Ollama는 서버가 고른 모델이 답합니다.
     */
    val models: List<String>,
    /** 서버가 대신 묻는지(CLI·Ollama). 열쇠 대신 서버 주소가 있어야 합니다. */
    val serverOnly: Boolean = false,
    /** 빈 모델을 화면에 적을 이름. 누가 모델을 정하는지가 공급자마다 다릅니다. */
    private val blankModelLabel: String = "CLI 기본"
) {
    OPENAI(
        label = "OpenAI",
        keyHint = "sk-로 시작합니다",
        models = listOf("gpt-6-luna", "gpt-6.1-terra", "gpt-6.1-sol", "gpt-6-astra")
    ),
    ANTHROPIC(
        label = "Claude",
        keyHint = "sk-ant-로 시작합니다",
        // 기록을 읽고 맥락을 짚어 주는 일이라 Opus를 앞에 둡니다. 그 위 급인 Fable은 값이 훨씬 높아 고를 수 있게만 둡니다.
        models = listOf("claude-opus-5-5", "claude-fable-5-1", "claude-sonnet-5-5", "claude-haiku-5-5")
    ),
    CLAUDE_CLI(
        label = "Claude CLI",
        keyHint = "",
        // Fable은 구독에서 사용 크레딧을 따로 켜야 돌고, 그 전에는 CLI가 거절합니다. 그래서 여기에는 두지 않습니다.
        models = listOf(CLI_DEFAULT, "claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5"),
        serverOnly = true
    ),
    CODEX_CLI(
        label = "Codex CLI",
        keyHint = "",
        models = listOf(CLI_DEFAULT, "gpt-6.1-sol", "gpt-6-astra", "gpt-6.1-terra", "gpt-6-luna"),
        serverOnly = true
    ),
    OLLAMA(
        label = "Ollama",
        keyHint = "",
        // 서버 PC에 어떤 모델을 받아 두었는지 이 기기는 모릅니다. 이름을 적어 두면 없는 모델을 고르게
        // 되므로, 서버가 고르게 둡니다(`APP_GOOD_WORDS_OLLAMA_MODEL`, 없으면 가장 최근에 받은 모델).
        models = listOf(CLI_DEFAULT),
        serverOnly = true,
        blankModelLabel = "서버 기본"
    );

    val defaultModel: String
        get() = models.first()

    /** 화면에 적을 모델 이름. 빈 값은 서버 쪽(CLI·Ollama)이 정한 기본 모델이라는 뜻입니다. */
    fun modelLabel(model: String): String = model.ifBlank { blankModelLabel }

    companion object {
        /** 서버 쪽 공급자의 기본 모델. 서버에 빈 값으로 보내면 CLI는 `--model`을 붙이지 않고, Ollama는 서버가 고릅니다. */
        const val CLI_DEFAULT_MODEL = CLI_DEFAULT

        /** 모르는 이름이 와도 버리지 않고 OpenAI로 읽습니다. 예전에 저장해 둔 설정이 살아 있어야 합니다. */
        fun of(name: String?): AiProvider =
            entries.firstOrNull { it.name == name?.trim()?.uppercase() } ?: OPENAI
    }
}
