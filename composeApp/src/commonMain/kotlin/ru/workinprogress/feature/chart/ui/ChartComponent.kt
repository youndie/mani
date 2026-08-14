package ru.workinprogress.feature.chart.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import org.koin.compose.module.rememberKoinModules
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import ru.workinprogress.feature.chart.ChartViewModel
import ru.workinprogress.feature.chart.GetChartUseCase
import ru.workinprogress.feature.chart.ui.model.ChartUi

@OptIn(KoinExperimentalAPI::class)
@Composable
fun ChartComponent(modifier: Modifier = Modifier, expanded: Boolean = false) {
    val viewModel = koinViewModel<ChartViewModel>()
    val state: ChartUi by viewModel.observe.collectAsStateWithLifecycle(ChartUi.Loading)

    ChartComponent(state, modifier, expanded)
}

@Composable
fun ChartComponent(state: ChartUi, modifier: Modifier = Modifier, expanded: Boolean = false) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.padding(16.dp).testTag("chartBox"),
    ) {
        ChartImpl(
            state.days.values.toImmutableList(),
            state.days.keys.groupBy { "${it.year}-${it.monthNumber}" }
                .map { it.value.first().format(format) }
                .toImmutableList(),
            todayIndexProvider = state.todayIndexProvider,
            currency = state.currency,
            loading = state.loading,
            expanded = expanded,
        )
    }
}

private val format = LocalDate.Format {
    monthName(MonthNames.ENGLISH_ABBREVIATED)
}
