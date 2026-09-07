package io.github.youndie.mani

import io.github.youndie.mani.data.networkModule
import io.github.youndie.mani.feature.auth.authModule
import io.github.youndie.mani.feature.categories.categoriesModule
import io.github.youndie.mani.feature.chart.chartModule
import io.github.youndie.mani.feature.currency.currencyModule
import io.github.youndie.mani.feature.transaction.transactionsModule
import org.koin.core.module.Module

val appModules: List<Module> = listOf(
    networkModule,
    authModule,
    chartModule,
    currencyModule,
    transactionsModule,
    categoriesModule,
)
