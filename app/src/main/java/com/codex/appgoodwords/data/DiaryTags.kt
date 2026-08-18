package com.codex.appgoodwords.data

/**
 * 일기에 붙이는 오늘의 날씨.
 *
 * DB와 동기화 JSON에는 [name]만 들어갑니다. 화면 문구를 고쳐도 저장된 값은 그대로입니다.
 * 모르는 값이 들어오면 [fromCode]가 null을 돌려주어 "고르지 않음"으로 보입니다.
 * 다음 버전에서 선택지를 늘렸을 때 옛 앱이 죽지 않게 하려는 것입니다.
 */
enum class DiaryWeather(val emoji: String, val label: String) {
    SUNNY("☀️", "맑음"),
    PARTLY_CLOUDY("⛅", "구름 조금"),
    CLOUDY("☁️", "흐림"),
    RAIN("🌧️", "비"),
    SNOW("❄️", "눈"),
    WIND("💨", "바람"),
    FOG("🌫️", "안개");

    companion object {
        fun fromCode(code: String?): DiaryWeather? {
            val trimmed = code?.trim().orEmpty()
            if (trimmed.isBlank()) return null
            return entries.firstOrNull { it.name == trimmed }
        }
    }
}

/** 일기에 붙이는 그날의 기분. 저장 방식은 [DiaryWeather]와 같습니다. */
enum class DiaryMood(val emoji: String, val label: String) {
    GREAT("😄", "아주 좋음"),
    GOOD("🙂", "좋음"),
    NEUTRAL("😐", "보통"),
    TIRED("😪", "지침"),
    ANGRY("😠", "화남"),
    SAD("😢", "슬픔"),
    BAD("🙁", "나쁨");

    companion object {
        fun fromCode(code: String?): DiaryMood? {
            val trimmed = code?.trim().orEmpty()
            if (trimmed.isBlank()) return null
            return entries.firstOrNull { it.name == trimmed }
        }
    }
}

/**
 * 일기의 종류.
 *
 * 자유 일기는 그날 있었던 일을 마음대로 적는 것이고, 감사·반성 일기는 물음에 답하는 것입니다.
 * 빈 화면 앞에서는 무엇을 쓸지 막막한데, 물음이 있으면 답만 채우면 되어 손이 훨씬 가볍습니다.
 *
 * 저장은 [name]으로 합니다. 모르는 값이 들어와도 자유 일기로 보고 앱이 죽지 않습니다.
 */
enum class DiaryKind(val label: String, val prompts: List<String>) {
    FREE("자유", emptyList()),
    GRATITUDE(
        "감사",
        listOf("오늘 감사한 일", "고마운 사람", "당연하지 않았던 것")
    ),
    REFLECTION(
        "반성",
        listOf("잘한 것", "아쉬운 것", "내일 바꿀 것")
    );

    val isGuided: Boolean
        get() = prompts.isNotEmpty()

    companion object {
        fun fromCode(code: String?): DiaryKind? {
            val trimmed = code?.trim().orEmpty()
            if (trimmed.isBlank()) return null
            return entries.firstOrNull { it.name == trimmed }
        }
    }
}

/** 일기 물음의 답을 다듬는 규칙. */
object DiaryAnswers {
    /**
     * 저장하기 전에 답을 다듬습니다.
     *
     * 앞뒤 공백을 떼고 뒤쪽 빈칸만 버립니다. 가운데 빈칸을 버리면 뒤의 답이 앞으로 밀려
     * 다른 물음의 답이 되어 버립니다. 뒤쪽 빈칸은 자리를 지킬 필요가 없어 버립니다.
     *
     * 서버 `normalizeAnswers()`와 같은 규칙이어야 합니다. 한쪽만 다르게 다듬으면
     * 같은 내용인데도 지문이 달라져 두 기기가 서로를 계속 고칩니다.
     */
    fun normalize(answers: List<String>): List<String> =
        answers.map { it.trim() }.dropLastWhile { it.isBlank() }

    /** 물음 개수에 맞춰 빈칸을 채운 목록. 화면에서 칸을 그릴 때 씁니다. */
    fun padded(answers: List<String>, size: Int): List<String> =
        List(size) { index -> answers.getOrNull(index).orEmpty() }
}
