package com.codex.appgoodwords.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
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
import com.codex.appgoodwords.data.RoutineEntity
import com.codex.appgoodwords.data.RoutineOrder

/**
 * 홈 화면에서 오늘 루틴을 바로 밟는 위젯입니다.
 *
 * 루틴은 하루에 여러 번 손이 가는 것이라, 앱을 열고 탭을 옮겨야 체크할 수 있으면
 * 그 몇 초가 매번 쌓입니다. 여기서 바로 누르면 하루가 이어집니다.
 *
 * **다음 차례 몇 개만 보여 줍니다.** 서른 개를 다 늘어놓으면 위젯이 목록이 되고,
 * 작은 칸에서는 어차피 잘립니다.
 */
class RoutineWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as AppGoodWordsApplication).container
        val state = loadState(container)

        provideContent {
            GlanceTheme {
                RoutineWidgetContent(state)
            }
        }
    }

    companion object {
        /** 위젯에 늘어놓을 줄 수. */
        const val VISIBLE_COUNT = 3

        suspend fun loadState(container: AppContainer): RoutineWidgetState {
            val routines = RoutineOrder.sorted(container.database.routineDao().getAll())
            val (start, end) = container.repository.todayRangeMillis()
            val checkedIds = container.database.routineCheckDao().getAll()
                .filter { it.checkedAt in start..end }
                .map { it.routineId }
                .toSet()

            return RoutineWidgetState(
                total = routines.size,
                doneCount = routines.count { it.id in checkedIds },
                // 아직 안 밟은 것부터 보여 줍니다. 이미 한 것을 위젯에 띄워 둘 이유가 없습니다.
                next = routines.filterNot { it.id in checkedIds }.take(VISIBLE_COUNT)
            )
        }
    }
}

data class RoutineWidgetState(
    val total: Int,
    val doneCount: Int,
    val next: List<RoutineEntity>
)

/** 위젯에서 루틴 하나를 밟고 다시 그립니다. */
class CheckRoutineAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val routineId = parameters[routineIdKey] ?: return
        val container = (context.applicationContext as AppGoodWordsApplication).container
        container.repository.markRoutineDone(routineId)
        RoutineWidget().update(context, glanceId)
    }

    companion object {
        val routineIdKey = ActionParameters.Key<Long>("routine_id")
    }
}

class RoutineWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RoutineWidget()
}

@androidx.compose.runtime.Composable
internal fun RoutineWidgetContent(state: RoutineWidgetState) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(18.dp)
            .padding(14.dp)
            .clickable(actionStartActivity(openAppIntent())),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = if (state.total == 0) {
                "루틴을 추가해 보세요"
            } else {
                "오늘 루틴 ${state.doneCount} / ${state.total}"
            },
            style = TextStyle(color = GlanceTheme.colors.onSurface)
        )
        Spacer(modifier = GlanceModifier.height(8.dp))

        if (state.next.isEmpty()) {
            Text(
                text = if (state.total == 0) "앱에서 하루의 차례를 정할 수 있습니다." else "오늘 몫을 다 밟았습니다.",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
            )
        } else {
            state.next.forEach { routine ->
                RoutineWidgetRow(routine)
                Spacer(modifier = GlanceModifier.height(6.dp))
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun RoutineWidgetRow(routine: RoutineEntity) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(GlanceTheme.colors.secondaryContainer)
            .cornerRadius(12.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            // 줄 전체가 눌립니다. 작은 체크 상자를 두면 위젯에서는 겨냥하기 어렵습니다.
            .clickable(
                actionRunCallback<CheckRoutineAction>(
                    actionParametersOf(CheckRoutineAction.routineIdKey to routine.id)
                )
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "○ ${routine.title}",
            style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer),
            maxLines = 1
        )
    }
}

private fun openAppIntent(): Intent = Intent().apply {
    setClassName("com.codex.appgoodwords", MainActivity::class.java.name)
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
}
