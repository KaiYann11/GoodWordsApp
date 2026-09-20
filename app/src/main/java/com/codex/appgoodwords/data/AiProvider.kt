package com.codex.appgoodwords.data

/** 열거 항목을 만들 때는 동반 객체가 아직 없어, 같은 값을 파일 자리에 둡니다. */
private const val CLI_DEFAULT = ""

/**
 * 돌아보기를 써 줄 AI.
 *
 * 부르는 길이 둘입니다. 열쇠로 API를 부르는 길과, 서버에 로그인해 둔 CLI(Claude Code·Codex)를
 * 서버가 대신 돌리는 길([serverCli])입니다. CLI 길은 열쇠가 없고 구독을 그대로 씁니다.
 * 대신 서버 없이는 부를 수 없습니다 — 이 기기에는 CLI가 없습니다.
 *
 * 열쇠는 공급자마다 따로 둡니다. 하나만 두면 다른 쪽으로 바꿔 볼 때 넣어 둔 열쇠가 지워져,
 * 되돌리려면 다시 넣어야 합니다.
 */
enum class AiProvider(
    val label: String,
    /** 열쇠 칸에 적어 줄 힌트. 잘못된 열쇠를 넣고 왜 안 되는지 모르는 일을 줄입니다. CLI에는 없습니다. */
    val keyHint: String,
    /**
     * 고를 수 있는 모델. 빈 문자열은 "CLI가 정한 기본 모델"이라는 뜻이라 CLI 공급자에만 있습니다.
     * 서버가 `--model`을 붙이지 않고 돌려, CLI에 설정해 둔 모델이 답합니다.
     */
    val models: List<String>,
    /** 서버에 깔린 CLI가 답하는지. 열쇠 대신 서버 주소가 있어야 합니다. */
    val serverCli: Boolean = false
) {
    OPENAI(
        label = "OpenAI",
        keyHint = "sk-로 시작합니다",
        models = listOf("gpt-4o-mini", "gpt-4o", "gpt-4.1-mini")
    ),
    ANTHROPIC(
        label = "Claude",
        keyHint = "sk-ant-로 시작합니다",
        // 기록을 읽고 맥락을 짚어 주는 일이라 Opus를 앞에 둡니다.
        models = listOf("claude-opus-5", "claude-sonnet-5", "claude-haiku-4-5")
    ),
    CLAUDE_CLI(
        label = "Claude CLI",
        keyHint = "",
        models = listOf(CLI_DEFAULT, "claude-opus-5", "claude-sonnet-5", "claude-haiku-4-5"),
        serverCli = true
    ),
    CODEX_CLI(
        label = "Codex CLI",
        keyHint = "",
        models = listOf(CLI_DEFAULT, "gpt-6-astra", "gpt-5.6-sol", "gpt-5.5"),
        serverCli = true
    );

    val defaultModel: String
        get() = models.first()

    /** 화면에 적을 모델 이름. 빈 값은 CLI의 기본 모델이라는 뜻입니다. */
    fun modelLabel(model: String): String = model.ifBlank { "CLI 기본" }

    companion object {
        /** CLI 공급자의 "CLI에 설정해 둔 모델". 서버에 빈 값으로 보내면 `--model`을 붙이지 않습니다. */
        const val CLI_DEFAULT_MODEL = CLI_DEFAULT

        /** 모르는 이름이 와도 버리지 않고 OpenAI로 읽습니다. 예전에 저장해 둔 설정이 살아 있어야 합니다. */
        fun of(name: String?): AiProvider =
            entries.firstOrNull { it.name == name?.trim()?.uppercase() } ?: OPENAI
    }
}
