package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 기분 추이 그래프의 세로 자리는 [DiaryMood]를 적어 둔 차례입니다.
 *
 * 차례를 바꾸면 그래프의 오르내림이 뒤집히는데, 화면에는 오류가 나지 않고 선 모양만 달라집니다.
 * 좋은 날이 아래로 내려가도 아무도 못 알아채므로 여기서 붙잡아 둡니다.
 */
class DiaryMoodRankTest {
    @Test
    fun theOrderRunsFromGoodToBad() {
        assertEquals(
            listOf(
                DiaryMood.GREAT,
                DiaryMood.GOOD,
                DiaryMood.NEUTRAL,
                DiaryMood.TIRED,
                DiaryMood.ANGRY,
                DiaryMood.SAD,
                DiaryMood.BAD
            ),
            DiaryMood.entries.sortedBy { it.rank }
        )
    }

    @Test
    fun theBestMoodSitsAtTheTop() {
        // 0이 맨 윗줄입니다. 그래프는 위가 좋은 쪽이어야 읽는 사람의 짐작과 맞습니다.
        assertEquals(0, DiaryMood.GREAT.rank)
        assertEquals(DiaryMood.entries.size - 1, DiaryMood.BAD.rank)
    }

    @Test
    fun neutralSitsInTheMiddle() {
        // 보통 자리에 눈금선을 긋습니다. 가운데가 아니면 위아래를 가르는 선의 뜻이 흐려집니다.
        val above = DiaryMood.entries.count { it.rank < DiaryMood.NEUTRAL.rank }
        val below = DiaryMood.entries.count { it.rank > DiaryMood.NEUTRAL.rank }

        assertTrue("보통 위가 $above, 아래가 $below 입니다.", above in 1..below)
    }

    @Test
    fun everyMoodHasItsOwnRow() {
        assertEquals(DiaryMood.entries.size, DiaryMood.rankCount)
        assertEquals(DiaryMood.entries.size, DiaryMood.entries.map { it.rank }.distinct().size)
    }
}
