package com.codex.appgoodwords.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.codex.appgoodwords.AppGoodWordsApplication

/** 위젯의 "다음 글귀"를 눌렀을 때 새 항목을 고르고 위젯을 다시 그린다. */
class NextQuoteAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val container = (context.applicationContext as AppGoodWordsApplication).container
        QuoteWidget.pickNextItem(container)
        QuoteWidget().update(context, glanceId)
    }
}
