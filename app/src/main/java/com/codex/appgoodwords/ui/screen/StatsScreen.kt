package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.RoutineCheckEntity
import com.codex.appgoodwords.data.RoutineEntity
import com.codex.appgoodwords.data.StatsSummary
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun StatsScreen(
    summary: StatsSummary,
    routines: List<RoutineEntity>,
    checks: List<RoutineCheckEntity>,
    modifier: Modifier = Modifier
) {
    var selectedMonthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedDateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            StatsCard(summary = summary)
        }

        item {
            RoutineCalendarCard(
                routines = routines,
                checks = checks,
                selectedMonthText = selectedMonthText,
                selectedDateText = selectedDateText,
                onMonthChanged = { month, date ->
                    selectedMonthText = month.toString()
                    selectedDateText = date.toString()
                },
                onDateSelected = { date ->
                    selectedDateText = date.toString()
                }
            )
        }
    }
}
