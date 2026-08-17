package com.codex.appgoodwords.data

/**
 * 오늘 읽은 글귀 수로 정하는 콤보.
 *
 * 예전에는 2초 안에 다시 스와이프하면 콤보가 올랐습니다. 그러면 빨리 넘기는 것이 잘하는 것이 되어,
 * 읽지 않고 쓸어 넘기는 쪽이 유리했습니다. 중요한 것은 속도가 아니라 그날 얼마나 봤느냐이므로,
 * 이제 그날 확인한 개수만 봅니다.
 *
 * 순수 함수라 기기 없이 검증할 수 있습니다.
 */
data class ComboLevel(
    /** 오늘 확인한 글귀 수. */
    val count: Int,
    val title: String,
    val message: String,
    /** 이정표에 닿은 순간인지. 축하 효과의 세기를 여기서 정합니다. */
    val isMilestone: Boolean,
    /** 다음 이정표까지 얼마나 왔는지(0~1). 마지막 이정표를 넘었으면 1입니다. */
    val progressToNext: Float,
    /** 다음 이정표. 더 없으면 null입니다. */
    val nextMilestone: Int?
)

object ReadingCombo {
    /** 하루에 닿을 만한 간격으로 둡니다. 너무 촘촘하면 축하가 흔해져 의미가 없어집니다. */
    val MILESTONES = listOf(5, 10, 20, 30, 50, 100)

    fun of(todayCount: Int): ComboLevel {
        val count = todayCount.coerceAtLeast(0)
        val next = MILESTONES.firstOrNull { it > count }
        val previous = MILESTONES.lastOrNull { it <= count } ?: 0

        return ComboLevel(
            count = count,
            title = title(count),
            message = message(count, next),
            isMilestone = count > 0 && count in MILESTONES,
            progressToNext = progress(count, previous, next),
            nextMilestone = next
        )
    }

    private fun title(count: Int): String = when (count) {
        0 -> "오늘은 아직"
        1 -> "오늘 첫 글귀"
        else -> "오늘 ${count}개"
    }

    private fun message(count: Int, next: Int?): String = when {
        count == 0 -> "한 개만 읽어도 오늘이 시작됩니다"
        count in MILESTONES -> "${count}개를 채웠습니다"
        next == null -> "오늘 충분히 읽었습니다"
        else -> "${next}개까지 ${next - count}개 남았습니다"
    }

    /**
     * 지난 이정표와 다음 이정표 사이에서의 위치입니다.
     *
     * 0부터 다시 세지 않는 이유는, 10을 넘긴 사람에게 20까지의 막대가 절반부터 시작해야
     * 방금 지나온 이정표가 헛되지 않게 보이기 때문입니다.
     */
    private fun progress(count: Int, previous: Int, next: Int?): Float {
        if (next == null) return 1f
        val span = next - previous
        if (span <= 0) return 1f
        return ((count - previous).toFloat() / span).coerceIn(0f, 1f)
    }
}
