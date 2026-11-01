package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 루틴을 합칠 때 이름과 메모를 고르는 규칙.
 *
 * 고르기(이것·그것)와 합치기(둘 다)가 같은 글을 두 번 적거나 빈 줄만 남기면, 합친 뒤 루틴 카드에서 바로 드러납니다.
 */
class RoutineMergeTextTest {
    @Test fun eachSideCanBePickedAsItIs() {
        assertEquals("아침 물 한 컵", RoutineMergeText.title(RoutineMergeChoice.THIS, " 아침 물 한 컵 ", "물 마시기"))
        assertEquals("물 마시기", RoutineMergeText.title(RoutineMergeChoice.THAT, "아침 물 한 컵", "물 마시기"))
    }

    @Test fun bothNamesSitOnOneLineAndTheSameNameIsNotRepeated() {
        assertEquals("아침 물 한 컵 · 물 마시기", RoutineMergeText.title(RoutineMergeChoice.BOTH, "아침 물 한 컵", "물 마시기"))
        assertEquals("물 마시기", RoutineMergeText.title(RoutineMergeChoice.BOTH, "물 마시기", " 물 마시기"))
    }

    @Test fun bothMemosAreJoinedByABlankLineAndAnEmptyOneIsLeftOut() {
        assertEquals("일어나자마자\n\n물병을 책상에", RoutineMergeText.note(RoutineMergeChoice.BOTH, "일어나자마자", "물병을 책상에"))
        // 한쪽 메모가 비어 있으면 빈 줄로 시작하지 않습니다.
        assertEquals("물병을 책상에", RoutineMergeText.note(RoutineMergeChoice.BOTH, "  ", "물병을 책상에"))
        assertEquals("", RoutineMergeText.note(RoutineMergeChoice.BOTH, "", ""))
    }
}
