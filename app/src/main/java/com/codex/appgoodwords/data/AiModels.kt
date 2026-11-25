package com.codex.appgoodwords.data

/** 고를 수 있는 모델 하나. [label]은 공급자가 붙인 이름입니다(없으면 [id]). */
data class AiModelInfo(
    val id: String,
    val label: String = id,
    /** 공급자가 알려 준 나온 때(밀리초). 모르면 0입니다. */
    val createdAt: Long = 0L
)

/**
 * 한 공급자에게서 받아 둔 모델 목록.
 *
 * 이 기기에만 둡니다. 동기화 스냅샷과 백업에는 담지 않습니다 — 기기마다 열쇠와 서버가 달라 받는 목록도 다를 수 있습니다.
 */
data class AiModelList(
    val models: List<AiModelInfo> = emptyList(),
    /** 받은 때. 0이면 받은 적이 없습니다. */
    val fetchedAt: Long = 0L
) {
    /** 받은 지 [maxAgeMillis]가 지났거나 받은 적이 없으면 새로 받을 때입니다. */
    fun isStale(now: Long, maxAgeMillis: Long): Boolean = fetchedAt <= 0L || now - fetchedAt > maxAgeMillis
}

/**
 * 칸마다 알아 둔 "지금 답하는 모델"(설정 → AI). 공급자마다 하나씩, 이 기기에만 둡니다.
 *
 * CLI·Ollama의 기본과 Claude CLI의 계열 칸은 서버 쪽이 모델을 골라서, 앱은 물어보기 전에는 이름을 모릅니다
 * ([AiModels.serverPickedChoices]). 그래서 "모델 정보 받아오기" 단추를 누르면 서버에 물어 적어 두고, AI가 답할 때마다 답한
 * 모델로 고쳐 둡니다(사용자가 정함, 2026-11-25). 1.26.0은 마지막 답 하나만 기억해서 칸을 바꾸면 이름이 사라졌습니다.
 * 열쇠로 부르는 곳의 칸은 받아 온 목록으로 셉니다 — 여기에는 단추를 누른 때만 남습니다.
 */
data class AiModelCheck(
    /** "모델 정보 받아오기"를 누른 때. 0이면 누른 적이 없어 단추 아래 목록을 보여 주지 않습니다. */
    val checkedAt: Long = 0L,
    /** 칸(빈 값·`latest:sonnet`·적어 둔 별칭) → 답하는 모델 이름. */
    val names: Map<String, String> = emptyMap(),
    /** 칸 → 지난번에 알아내지 못한 사유. 그 칸으로 답을 받으면 지웁니다. */
    val errors: Map<String, String> = emptyMap()
) {
    /** AI가 [model]로 답했습니다. 그 칸의 이름을 고치고 지난 실패는 지웁니다. 단추를 누른 때는 그대로 둡니다. */
    fun withAnswer(choice: String, model: String): AiModelCheck =
        copy(names = names + (choice to model), errors = errors - choice)
}

/** 서버가 알아 온 이름 하나(`POST /api/ai-models/resolve`). 알아내지 못하면 [model]이 비고 [error]에 사유가 있습니다. */
data class AiResolvedModel(val model: String, val error: String = "")

/** 모델 목록·모델 정보를 받는 중인 공급자와, 공급자마다 마지막으로 받지 못한 사유. 화면에만 둡니다. */
data class AiModelRefreshState(
    val loading: AiProvider? = null,
    val errors: Map<AiProvider, String> = emptyMap(),
    /** "모델 정보 받아오기"가 도는 공급자. */
    val checking: AiProvider? = null,
    /** 공급자마다 "모델 정보 받아오기"가 받지 못한 사유(목록이나 서버). 칸 하나의 실패는 [AiModelCheck.errors]에 있습니다. */
    val checkErrors: Map<AiProvider, String> = emptyMap()
)

/**
 * 모델 고르기.
 *
 * 예전에는 앱이 공급자마다 모델 이름을 들고 있었고, 목록에 없는 이름은 기본 모델로 되돌렸습니다. 그래서 새 모델이 나와도
 * 앱을 다시 게시해야 보였습니다(10월 29일에 고친 목록에 11월에 나온 Claude Fable 5.2가 없었습니다). 이제 **계열("Sonnet 최신")을
 * 고르면 그 계열에서 가장 새 모델을 그때그때 부릅니다**(사용자가 정함, 2026-11-25). 목록은 공급자·서버에서 받아 두고
 * ([AiModelList]), 받지 못했을 때만 앱에 든 것([AiProvider.builtInModels])을 씁니다.
 *
 * 설정에 저장되는 값([AiFeedbackSettings.model])은 셋 중 하나입니다.
 * - 빈 값: 공급자 기본. 열쇠로 부르는 곳은 기본 계열의 최신([AiProvider.defaultFamily]), CLI·Ollama는 서버 쪽 기본입니다.
 * - `latest:<계열>`: 그 계열에서 가장 새 모델([latest]).
 * - 그 밖의 글: 그 이름 그대로("다른 모델…"에서 고르거나 적은 것). 그 모델이 없어지면 공급자가 거절하는 사유가 그대로 보입니다.
 */
object AiModels {
    const val LATEST_PREFIX = "latest:"

    /** 1.25.x까지 저장한 모델 이름을 이 꼴로 옮겼는지([migrateLegacy]). 설정을 한 번 저장하면 이 판이 적힙니다. */
    const val SCHEME = 2

    fun latest(family: String): String = LATEST_PREFIX + family

    /** `latest:sonnet` → `sonnet`. 계열을 고른 것이 아니면 null입니다. */
    fun familyOfChoice(choice: String): String? =
        choice.trim().takeIf { it.startsWith(LATEST_PREFIX) }?.removePrefix(LATEST_PREFIX)?.takeIf { it.isNotBlank() }

    /** 칸에 적을 이름. `opus` → "Opus 최신". */
    fun familyLabel(family: String): String = family.replaceFirstChar { it.uppercase() } + " 최신"

    /**
     * 이름에서 계열을 읽습니다 — `claude-opus-5-5` → `opus`, `gpt-6.1-terra` → `terra`.
     *
     * 세대 번호를 뺀 이름이 계열입니다. 날짜가 붙은 판(`claude-haiku-4-5-20251001`)도 같은 계열입니다. 꼴이 다른 이름
     * (`gpt-4o-mini`, Ollama의 `qwen3.6:27b`, 날짜가 붙은 OpenAI 판)은 계열이 없어 "다른 모델…"에서만 고릅니다.
     */
    fun familyOf(id: String): String? = parse(id)?.family

    /** 세대 번호. `claude-opus-5-5` → [5, 5], `gpt-6.1-terra` → [6, 1]. 날짜 꼬리는 세지 않습니다. */
    fun versionOf(id: String): List<Int> = parse(id)?.version.orEmpty()

    /**
     * 그 계열에서 가장 새 모델.
     *
     * 이름의 세대 번호로 셉니다(목록의 차례는 공급자마다 달라 믿지 않습니다). 세대가 같으면 날짜 꼬리가 없는 이름 — 그
     * 세대의 가장 새 판을 늘 가리키는 이름 — 을 먼저 고르고, 그래도 같으면 목록에서 앞에 있는 것입니다.
     */
    fun newestIn(family: String, models: List<AiModelInfo>): AiModelInfo? {
        var best: AiModelInfo? = null
        var bestName: ParsedName? = null
        for (model in models) {
            val parsed = parse(model.id) ?: continue
            if (parsed.family != family) continue
            if (bestName == null || isNewer(parsed, bestName)) {
                best = model
                bestName = parsed
            }
        }
        return best
    }

    /**
     * 칸으로 보일 계열.
     *
     * [preferred](공급자가 정한 차례)에 있는 것을 먼저, 받아 온 목록에 새로 생긴 계열은 그 뒤에 목록 차례로 붙입니다 —
     * 새 계열이 나와도 앱을 고치지 않고 칸이 생깁니다. 다만 가장 새 세대보다 두 세대 이상 뒤진 계열은 뺍니다. OpenAI 목록에는
     * 몇 세대 전 계열까지 남아 있어 칸이 끝없이 늘어납니다(그런 모델은 "다른 모델…"에서 고릅니다).
     */
    fun families(models: List<AiModelInfo>, preferred: List<String>): List<String> {
        val newest = LinkedHashMap<String, List<Int>>()
        for (model in models) {
            val parsed = parse(model.id) ?: continue
            val known = newest[parsed.family]
            if (known == null || compareVersions(parsed.version, known) > 0) newest[parsed.family] = parsed.version
        }
        val topMajor = newest.values.maxOfOrNull { it.firstOrNull() ?: 0 } ?: return emptyList()
        val kept = newest.filterValues { (it.firstOrNull() ?: 0) >= topMajor - 1 }.keys
        return preferred.filter { it in kept } + kept.filter { it !in preferred }
    }

    /**
     * OpenAI 목록에서 글로 묻고 답하는 모델만 고릅니다. 그 목록에는 그림·소리·임베딩·검열 모델까지 다 들어 있어, 그것을
     * 고르면 돌아보기가 "지원하지 않는 모델"로만 끝납니다. **서버 `isOpenAiChatModel`과 같은 규칙**입니다.
     */
    fun isOpenAiChatModel(id: String): Boolean =
        OPENAI_CHAT.containsMatchIn(id) && OPENAI_NON_CHAT_WORDS.none { id.contains(it) }

    /** 직접 적은 이름으로 받을 수 있는지. 빈칸·따옴표처럼 서버가 CLI에 넘기지 않는 글자는 막습니다. */
    fun isValidName(name: String): Boolean = NAME.matches(name.trim())

    /**
     * 서버에 물어야 이름을 아는 칸 — CLI·Ollama의 기본(빈 값), Claude CLI의 계열 칸과 적어 둔 별칭(`fable`).
     *
     * 나머지 칸은 앱이 보낼 이름을 이미 압니다(받아 온 목록에서 셉니다 — [AiFeedbackSettings.requestModel]). Claude CLI에
     * 적은 온전한 이름(`claude-fable-5-2`)도 그 이름 그대로 답합니다.
     *
     * @param current 지금 고른 것. Claude CLI에 별칭을 적어 골랐으면 그것도 알아봅니다.
     */
    fun serverPickedChoices(provider: AiProvider, current: String): List<String> = buildList {
        if (provider.defaultFamily == null) add("")
        if (provider.familyAliases) {
            provider.familyChoices.forEach { add(latest(it)) }
            val typed = current.trim()
            if (typed.isNotEmpty() && familyOfChoice(typed) == null && familyOf(typed) == null) add(typed)
        }
    }

    /** 서버가 모델을 고르는 칸인지([serverPickedChoices]). */
    fun isServerPicked(provider: AiProvider, choice: String): Boolean = choice in serverPickedChoices(provider, choice)

    /**
     * 1.25.x까지 저장한 모델 이름을 이 꼴로 옮깁니다.
     *
     * 예전 판은 목록에 있는 이름(`claude-sonnet-5-5`)을 그대로 저장했고, 목록에서 빠진 이름은 기본으로 돌렸습니다. 그 이름을
     * 같은 계열의 최신으로 옮깁니다 — 지금은 같은 모델이고, 다음 세대가 나오면 저절로 넘어갑니다(사용자가 고른 것, 2026-11-25).
     * 계열을 읽을 수 없는 이름은 예전처럼 기본으로 돌립니다.
     */
    fun migrateLegacy(provider: AiProvider, stored: String): String {
        val name = stored.trim()
        if (name.isEmpty() || name.startsWith(LATEST_PREFIX)) return name
        // Ollama는 "서버 기본" 하나뿐이었습니다.
        if (provider == AiProvider.OLLAMA) return ""
        val family = familyOf(name) ?: return ""
        if (provider.familyAliases && family !in provider.familyChoices) return ""
        return latest(family)
    }

    private data class ParsedName(val family: String, val version: List<Int>, val dated: Boolean)

    private fun parse(id: String): ParsedName? {
        val name = id.trim()
        CLAUDE.matchEntire(name)?.let { match ->
            return ParsedName(
                family = match.groupValues[1],
                version = numbers(match.groupValues[2], '-'),
                dated = match.groupValues[3].isNotEmpty()
            )
        }
        // 3.x 때의 꼴(`claude-3-5-sonnet-20241022`). 지금은 다 거둬졌지만 목록에 남아 있어도 같은 계열로 읽습니다.
        CLAUDE_OLD.matchEntire(name)?.let { match ->
            return ParsedName(
                family = match.groupValues[2],
                version = numbers(match.groupValues[1], '-'),
                dated = match.groupValues[3].isNotEmpty()
            )
        }
        GPT.matchEntire(name)?.let { match ->
            return ParsedName(family = match.groupValues[2], version = numbers(match.groupValues[1], '.'), dated = false)
        }
        return null
    }

    private fun numbers(text: String, separator: Char): List<Int> =
        text.split(separator).filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }

    private fun isNewer(candidate: ParsedName, best: ParsedName): Boolean {
        val byVersion = compareVersions(candidate.version, best.version)
        if (byVersion != 0) return byVersion > 0
        return !candidate.dated && best.dated
    }

    /** 앞자리부터 견줍니다. 없는 자리는 0으로 봅니다(`5` = `5.0` < `5.5`). */
    private fun compareVersions(a: List<Int>, b: List<Int>): Int {
        for (index in 0 until maxOf(a.size, b.size)) {
            val diff = a.getOrElse(index) { 0 } - b.getOrElse(index) { 0 }
            if (diff != 0) return diff
        }
        return 0
    }

    /** `claude-<계열>-<세대>`, 끝에 날짜(8자리)가 붙기도 합니다. 세대 한 자리는 두 자리까지입니다(날짜와 가르려고). */
    private val CLAUDE = Regex("""claude-([a-z]+)((?:-\d{1,2})*)(?:-(\d{8}))?""")
    private val CLAUDE_OLD = Regex("""claude-(\d{1,2}(?:-\d{1,2})*)-([a-z]+)(?:-(\d{8}))?""")
    /** `gpt-<세대>-<계열>`. 날짜가 붙은 판(`gpt-6-luna-2026-10-15`)은 계열로 셈하지 않습니다 — 별칭이 늘 있습니다. */
    private val GPT = Regex("""gpt-(\d+(?:\.\d+)?)-([a-z]+)""")
    private val OPENAI_CHAT = Regex("""^(gpt-\d|o\d)""")
    private val OPENAI_NON_CHAT_WORDS = listOf(
        "audio", "realtime", "transcribe", "tts", "search", "image", "embedding",
        "moderation", "instruct", "codex", "computer", "whisper", "dall-e"
    )

    /** 서버의 CLI 모델 이름 검사(`aiModelPattern`)보다 조금 넓습니다 — Ollama 이름에는 `/`가 들어가기도 합니다. */
    private val NAME = Regex("""[A-Za-z0-9][A-Za-z0-9._:/-]{0,99}""")
}
