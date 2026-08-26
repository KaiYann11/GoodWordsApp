package com.codex.appgoodwords.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 되돌아볼 기간.
 *
 * 주기로 돌 때는 이 단위로 기간을 잘라 냅니다. 손으로 부를 때는 [MANUAL]이고,
 * 이때도 기간은 정해집니다. 언제부터 언제까지를 보고 쓴 글인지 남겨 두지 않으면
 * 나중에 그 문장이 무엇을 근거로 나왔는지 알 수 없습니다.
 */
enum class ReportPeriod(val label: String, val days: Int) {
    DAILY("하루", 1),
    WEEKLY("한 주", 7),
    MONTHLY("한 달", 30),
    MANUAL("직접 부름", 7);

    companion object {
        fun of(name: String?): ReportPeriod =
            entries.firstOrNull { it.name == name } ?: MANUAL
    }
}

/**
 * AI가 써 준 성장 피드백 한 편.
 *
 * 받아 온 글을 그대로 두지 않고 갈래별로 나눠 담습니다. 화면이 다시 쪼개지 않아도 되고,
 * 나중에 "추천 루틴만 모아 보기" 같은 것을 붙일 때 다시 읽어 낼 필요가 없습니다.
 *
 * 기기 사이에서는 [syncId]로만 짝을 짓습니다. 숫자 id는 기기마다 따로 증가합니다.
 */
@Entity(
    tableName = "growth_reports",
    indices = [
        Index(value = ["syncId"], unique = true),
        Index(value = ["createdAt"])
    ]
)
data class GrowthReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = SyncIdentity.newId(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** [ReportPeriod]의 이름. 모르는 값이 들어와도 버리지 않고 MANUAL로 읽습니다. */
    val period: String = ReportPeriod.MANUAL.name,
    /** 되돌아본 기간. ISO `yyyy-MM-dd`. */
    val periodStart: String = "",
    val periodEnd: String = "",
    /** 어느 모델이 썼는지. 나중에 문장 결이 달라진 이유를 찾을 때 씁니다. */
    val model: String = "",
    val strengths: List<String> = emptyList(),
    val improvements: List<String> = emptyList(),
    /** 오늘 곁에 둘 만한 글귀. 보관함에 담을 수 있게 따로 둡니다. */
    val suggestedQuote: String = "",
    val suggestedQuoteAuthor: String = "",
    /** 해 볼 만한 루틴. 루틴으로 바로 만들 수 있게 이름만 담습니다. */
    val suggestedRoutines: List<String> = emptyList(),
    val guide: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    val hasContent: Boolean
        get() = strengths.isNotEmpty() ||
            improvements.isNotEmpty() ||
            suggestedRoutines.isNotEmpty() ||
            suggestedQuote.isNotBlank() ||
            guide.isNotBlank()
}
