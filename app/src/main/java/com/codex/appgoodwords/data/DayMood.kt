package com.codex.appgoodwords.data

import java.time.LocalDate

/**
 * 그날의 기분과, 그것을 얼마나 믿을 수 있는지.
 *
 * [settled]가 false면 그날 일기들이 서로 다른 기분이었고, 그중 마지막에 남긴 것을 골라 온
 * 것입니다. 그래프는 이것도 씁니다. 안 그리면 실제로는 쓴 날인데 안 쓴 날처럼 비어 보입니다.
 * 기분별로 실천을 평균 내는 자리는 이런 날을 뺍니다. 어느 쪽이라 할 수 없는 날을 한 칸에
 * 넣으면 그 칸의 평균이 흔들립니다.
 */
data class DayMoodEntry(
    val mood: DiaryMood,
    val settled: Boolean
)

/**
 * 그날의 기분이 무엇이었는지.
 *
 * 기분이 두 곳에 남습니다. 톡 찍어 둔 것([MoodLogEntity])과 일기에 딸린 것입니다.
 * 겹쳐 보는 자리마다 각자 셈하면 화면끼리 다른 말을 하게 되므로, 한곳에서 정합니다.
 *
 * 규칙은 셋입니다.
 *
 * 1. **톡 찍은 것이 먼저입니다.** 하루의 기분을 묻는 자리에서 직접 고른 것이고,
 *    일기의 기분은 그 일기에 딸린 것입니다. 둘이 다르면 하루 쪽을 따릅니다.
 * 2. 찍어 둔 것이 없으면 일기에서 봅니다. 예전에 쓴 기록이 그대로 살아 있어야 합니다.
 * 3. 같은 날 일기 둘이 서로 다른 기분이면 마지막에 남긴 것을 고르되, **정해지지 않은 날로
 *    표시합니다**([DayMoodEntry.settled]). 감사 일기는 좋음, 반성 일기는 지침이면 그날을
 *    어느 쪽이라 딱 잘라 말할 수 없습니다. 무엇을 버릴지는 쓰는 쪽이 정합니다.
 *
 * 순수 함수라 기기 없이 검증할 수 있습니다.
 */
object DayMood {
    /** 날짜별 기분. 알 수 없는 날은 아예 담기지 않습니다. */
    fun entriesByDate(
        logs: List<MoodLogEntity>,
        diaries: List<DiaryEntity>
    ): Map<LocalDate, DayMoodEntry> {
        val byDate = mutableMapOf<LocalDate, DayMoodEntry>()

        // 하루에 여러 편을 썼으면 그날을 닫으며 남긴 것이 그날에 가깝습니다.
        diaries
            .mapNotNull { diary ->
                val date = parseDate(diary.entryDate) ?: return@mapNotNull null
                val mood = diary.moodOption ?: return@mapNotNull null
                Triple(date, mood, diary.createdAt)
            }
            .groupBy({ it.first }, { it.second to it.third })
            .forEach { (date, sameDay) ->
                byDate[date] = DayMoodEntry(
                    mood = sameDay.maxBy { (_, createdAt) -> createdAt }.first,
                    settled = sameDay.map { (mood, _) -> mood }.distinct().size == 1
                )
            }

        // 찍어 둔 것이 일기를 덮습니다. 같은 날 것이 둘이면 나중에 고친 쪽입니다.
        logs
            .sortedBy { it.updatedAt }
            .forEach { log ->
                val date = parseDate(log.entryDate) ?: return@forEach
                val mood = log.moodOption
                // 병합으로 빈 값이 올 수 있습니다. 지운 것을 일기 기분으로 되살리면 안 됩니다.
                if (mood == null) byDate.remove(date)
                else byDate[date] = DayMoodEntry(mood = mood, settled = true)
            }

        return byDate
    }

    /** 그날의 기분. 정해지지 않은 날도 담깁니다. 그래프처럼 빈 날이 오해를 낳는 자리에서 씁니다. */
    fun byDate(logs: List<MoodLogEntity>, diaries: List<DiaryEntity>): Map<LocalDate, DiaryMood> =
        entriesByDate(logs, diaries).mapValues { (_, entry) -> entry.mood }

    /** 어느 쪽이라 할 수 있는 날만. 기분별로 실천을 평균 내는 자리에서 씁니다. */
    fun settledByDate(logs: List<MoodLogEntity>, diaries: List<DiaryEntity>): Map<LocalDate, DiaryMood> =
        entriesByDate(logs, diaries)
            .filterValues { it.settled }
            .mapValues { (_, entry) -> entry.mood }

    /** 그날 찍어 둔 것. 고쳐 넣을 자리를 찾을 때 씁니다. 일기는 보지 않습니다. */
    fun logOn(logs: List<MoodLogEntity>, date: LocalDate): MoodLogEntity? = logs
        .filter { it.entryDate == date.toString() }
        .maxByOrNull { it.updatedAt }

    private fun parseDate(value: String): LocalDate? =
        runCatching { LocalDate.parse(value) }.getOrNull()
}
