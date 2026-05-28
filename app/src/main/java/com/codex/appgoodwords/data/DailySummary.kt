package com.codex.appgoodwords.data

data class DailySummary(
    val shownItems: List<DailySummaryLine>,
    val confirmedItems: List<DailySummaryLine>
) {
    val totalShown: Int get() = shownItems.sumOf { it.count }
    val totalConfirmed: Int get() = confirmedItems.sumOf { it.count }
}

data class DailySummaryLine(
    val title: String,
    val count: Int
)
