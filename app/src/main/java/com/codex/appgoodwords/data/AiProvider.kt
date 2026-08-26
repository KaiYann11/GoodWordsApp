package com.codex.appgoodwords.data

/**
 * 돌아보기를 써 줄 AI.
 *
 * 열쇠는 공급자마다 따로 둡니다. 하나만 두면 다른 쪽으로 바꿔 볼 때 넣어 둔 열쇠가 지워져,
 * 되돌리려면 다시 넣어야 합니다.
 */
enum class AiProvider(
    val label: String,
    /** 열쇠 칸에 적어 줄 힌트. 잘못된 열쇠를 넣고 왜 안 되는지 모르는 일을 줄입니다. */
    val keyHint: String,
    val models: List<String>
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
    );

    val defaultModel: String
        get() = models.first()

    companion object {
        /** 모르는 이름이 와도 버리지 않고 OpenAI로 읽습니다. 예전에 저장해 둔 설정이 살아 있어야 합니다. */
        fun of(name: String?): AiProvider =
            entries.firstOrNull { it.name == name?.trim()?.uppercase() } ?: OPENAI
    }
}
