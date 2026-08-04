package com.codex.appgoodwords.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.codex.appgoodwords.AppGoodWordsApplication
import com.codex.appgoodwords.MainActivity
import com.codex.appgoodwords.data.AppContainer
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ExposureTrigger
import com.codex.appgoodwords.work.AppNotifications

/**
 * 홈 화면에 글귀를 상시 노출하는 위젯입니다.
 *
 * 앱의 목적이 반복 노출인데 기존 노출 경로는 알림과 앱 실행뿐이라 위젯을 추가했습니다.
 * 위젯이 고른 항목도 SURFACED로 기록되므로 [com.codex.appgoodwords.data.AppRepository]의
 * 노출 순환에 그대로 반영됩니다.
 */
class QuoteWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as AppGoodWordsApplication).container
        val item = currentItem(container)

        provideContent {
            GlanceTheme {
                WidgetContent(item)
            }
        }
    }

    /**
     * 위젯이 마지막으로 고른 항목을 보여주고, 없거나 지워졌으면 새로 고른다.
     * 위젯을 처음 놓았을 때도 빈 화면 대신 바로 글귀가 보이게 하기 위함이다.
     */
    private suspend fun currentItem(container: AppContainer): ContentItemEntity? {
        val savedId = container.settingsStore.getWidgetContentId()
        val saved = if (savedId > 0L) container.repository.getContentById(savedId) else null
        if (saved != null) return saved

        return pickNextItem(container)
    }

    companion object {
        /** 위젯에 보여줄 다음 항목을 고르고 노출로 기록한다. */
        suspend fun pickNextItem(container: AppContainer): ContentItemEntity? {
            val settings = container.settingsStore.getSettings()
            val item = container.repository.pickFeaturedContent(
                category = settings.categoryFilter,
                trigger = ExposureTrigger.WIDGET_REFRESH
            ) ?: return null
            container.settingsStore.setWidgetContentId(item.id)
            return item
        }
    }
}

@androidx.compose.runtime.Composable
internal fun WidgetContent(item: ContentItemEntity?) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(16.dp),
        verticalAlignment = Alignment.Vertical.Top
    ) {
        if (item == null) {
            Text(
                text = "보관함에 글귀를 추가하면 여기에 표시됩니다.",
                style = TextStyle(color = GlanceTheme.colors.onSurface)
            )
            Spacer(modifier = GlanceModifier.height(8.dp))
            Text(
                text = "앱 열기",
                modifier = GlanceModifier.clickable(actionStartActivity(launchAppIntent())),
                style = TextStyle(color = GlanceTheme.colors.primary)
            )
            return@Column
        }

        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxWidth()
                .clickable(actionStartActivity(openItemIntent(item)))
        ) {
            if (item.title.isNotBlank()) {
                Text(
                    text = item.title,
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface)
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
            }
            Text(
                text = item.body,
                maxLines = 4,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
            )
            if (item.author.isNotBlank()) {
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = "— ${item.author}",
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
                )
            }
        }

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Horizontal.End
        ) {
            Text(
                text = "다음 글귀",
                modifier = GlanceModifier
                    .clickable(actionRunCallback<NextQuoteAction>())
                    .padding(top = 8.dp),
                style = TextStyle(color = GlanceTheme.colors.primary)
            )
        }
    }
}

/** 위젯을 누르면 알림과 같은 방식으로 해당 항목 상세로 들어간다. */
private fun openItemIntent(item: ContentItemEntity): Intent = launchAppIntent().apply {
    putExtra(AppNotifications.extraContentId, item.id)
    putExtra(AppNotifications.extraMarkConfirmed, false)
    putExtra(AppNotifications.extraRecordView, true)
}

private fun launchAppIntent(): Intent = Intent().apply {
    setClassName("com.codex.appgoodwords", MainActivity::class.java.name)
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
}
