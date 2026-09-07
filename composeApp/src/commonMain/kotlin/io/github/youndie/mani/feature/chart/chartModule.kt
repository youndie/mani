package io.github.youndie.mani.feature.chart

import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf

val chartModule = org.koin.dsl.module {
    singleOf(::GetChartUseCase)
    viewModelOf(::ChartViewModel)
}
