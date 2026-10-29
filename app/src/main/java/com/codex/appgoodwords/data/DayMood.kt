package com.codex.appgoodwords.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 그날의 기분과, 그것을 얼마나 믿을 수 있는지.
 *
 * [settled]가 false면 그날 찍은 기분들(없으면 일기들)이 서로 달랐고, 마지막에 남긴 것을 골라 온
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
 * 2. 읽을 수 있는 누름이 없으면 일기에서 봅니다. 예전에 쓴 기록이 그대로 살아 있어야 합니다.
 * 3. 같은 날 찍은 기분이 서로 다르면 마지막 것을 고르되, **정해지지 않은 날로 표시합니다**
 *    (사용자가 정함, 2026-10-29). 일기만 있는 날도 같은 규칙입니다([DayMoodEntry.settled]).
 *    아침에는 좋음, 저녁에는 지침이면 그날을
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

        // 찍어 둔 것이 일기를 덮습니다. 일기와 다르다는 이유로 settled를 바꾸지는 않습니다.
        logs
            .mapNotNull { log ->
                // 저장은 DiaryMood만 받고 JSON 가져오기·서버 정규화는 빈 기분을 거릅니다.
                // 다만 Room에 직접 넣은 손상 값이나 아직 모르는 코드는 있을 수 있습니다.
                // 삭제는 빈 값이 아니라 삭제 표식이므로, 못 읽는 한 건이 다른 기록을 지우면 안 됩니다.
                if (log.moodOption == null) return@mapNotNull null
                parseDate(log.entryDate)?.let { it to log }
            }
            .groupBy({ it.first }, { it.second })
            .forEach { (date, sameDay) ->
                val mood = sameDay.maxWith(logOrder).moodOption ?: return@forEach
                byDate[date] = DayMoodEntry(
                    mood = mood,
                    settled = sameDay.all { it.moodOption == mood }
                )
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

    /** 그날 찍은 흐름. 같은 시각이면 생성 시각·syncId로 정해 기기와 입력 순서에 흔들리지 않습니다. */
    fun logsOn(logs: List<MoodLogEntity>, date: LocalDate): List<MoodLogEntity> = logs
        .filter { it.entryDate == date.toString() }
        .sortedWith(logOrder)

    /** 바로 앞 기록. 연속해서 잘못 누른 것인지 볼 때도 흐름과 같은 차례를 씁니다. */
    fun logOn(logs: List<MoodLogEntity>, date: LocalDate): MoodLogEntity? = logsOn(logs, date).lastOrNull()

    /**
     * 예전에는 다시 찍으면 updatedAt만 바뀌었습니다. 남아 있는 기분을 고른 때는 생성 시각이
     * 아니라 이 시각입니다. 새 기록은 둘을 같게 적어 옛 기록과 같은 흐름에 놓습니다.
     * 이미 덮인 기분은 복원하지 않습니다. 웹도 이 시각을 씁니다.
     */
    fun recordedAt(log: MoodLogEntity): Long = log.updatedAt

    fun timeLabel(log: MoodLogEntity, zoneId: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(recordedAt(log)).atZone(zoneId).format(clock)

    /**
     * 손을 떼며 다시 누르거나 저장 반응을 기다리며 누르는 3초 미만만 막습니다. 분 단위로 막으면
     * 실제로 다시 남기려는 기분까지 잃습니다. 다른 기분을 거쳤으면 곧바로 돌아와도 새 기록입니다.
     * 시계가 뒤로 움직였다는 이유로 이후 기록을 계속 막지 않도록 음수 간격은 제외합니다.
     */
    fun isImmediateRepeat(last: MoodLogEntity?, mood: DiaryMood, now: Long): Boolean =
        last != null && last.moodOption == mood && now - recordedAt(last) in 0L until 3_000L

    private val logOrder = compareBy<MoodLogEntity> { recordedAt(it) }
        .thenBy { it.createdAt }.thenBy { it.syncId }
    private val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    private fun parseDate(value: String): LocalDate? =
        runCatching { LocalDate.parse(value) }.getOrNull()
}
