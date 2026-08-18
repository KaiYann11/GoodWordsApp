package com.codex.appgoodwords.widget

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ContentType
import com.codex.appgoodwords.data.WidgetSummary
import org.junit.Test

/**
 * 위젯 렌더링은 홈 화면에 붙여야만 보이므로, 컴포저블 단계에서라도
 * 항목이 있을 때와 없을 때 무엇이 나오는지 확인합니다.
 */
class QuoteWidgetContentTest {
    @Test
    fun showsQuoteFields() = runGlanceAppWidgetUnitTest {
        provideComposable {
            WidgetContent(
                ContentItemEntity(
                    id = 1L,
                    type = ContentType.QUOTE,
                    title = "기록하는 습관",
                    body = "매일 한 문장씩 남긴다.",
                    author = "작자 미상"
                )
            )
        }

        onNode(hasText("기록하는 습관")).assertExists()
        onNode(hasText("매일 한 문장씩 남긴다.")).assertExists()
        onNode(hasText("— 작자 미상")).assertExists()
        onNode(hasText("다음 글귀")).assertExists()
    }

    @Test
    fun omitsAuthorLineWhenBlank() = runGlanceAppWidgetUnitTest {
        provideComposable {
            WidgetContent(
                ContentItemEntity(
                    id = 1L,
                    type = ContentType.QUOTE,
                    title = "제목",
                    body = "본문",
                    author = ""
                )
            )
        }

        onNode(hasText("본문")).assertExists()
        onNode(hasText("—")).assertDoesNotExist()
    }

    @Test
    fun showsEmptyStateWhenNoItem() = runGlanceAppWidgetUnitTest {
        provideComposable { WidgetContent(null) }

        onNode(hasText("보관함에 글귀를 추가하면 여기에 표시됩니다.")).assertExists()
        onNode(hasText("앱 열기")).assertExists()
        onNode(hasText("다음 글귀")).assertDoesNotExist()
    }

    @Test
    fun showsTodayWorkUnderTheQuote() = runGlanceAppWidgetUnitTest {
        provideComposable {
            WidgetContent(
                quote(),
                WidgetSummary(remainingTodos = 2, overdueTodos = 1, readingTitle = "몰입의 즐거움", readingPercent = 34)
            )
        }

        // 앱을 열지 않고도 오늘 무엇이 남았는지 보이는 것이 위젯을 놓는 이유입니다.
        onNode(hasText("할 일 2개 · 지난 일 1개")).assertExists()
        onNode(hasText("읽는 중 · 몰입의 즐거움 34%")).assertExists()
    }

    @Test
    fun nothingIsAddedWhenThereIsNoWorkLeft() = runGlanceAppWidgetUnitTest {
        provideComposable { WidgetContent(quote()) }

        // 할 것이 없는데 "할 일 0개"를 띄우면 글귀 자리만 줄어듭니다.
        onNode(hasText("할 일 0개")).assertDoesNotExist()
        onNode(hasText("읽는 중")).assertDoesNotExist()
    }

    @Test
    fun onlyTheBookLineShowsWhenNothingIsDue() = runGlanceAppWidgetUnitTest {
        provideComposable {
            WidgetContent(
                quote(),
                WidgetSummary(remainingTodos = 0, overdueTodos = 0, readingTitle = "읽는 책", readingPercent = null)
            )
        }

        onNode(hasText("읽는 중 · 읽는 책")).assertExists()
        onNode(hasText("할 일 0개")).assertDoesNotExist()
    }

    private fun quote() = ContentItemEntity(
        id = 1L,
        type = ContentType.QUOTE,
        title = "제목",
        body = "본문"
    )
}
