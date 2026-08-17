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
