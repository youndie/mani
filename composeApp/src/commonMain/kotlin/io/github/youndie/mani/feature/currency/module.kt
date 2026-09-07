package io.github.youndie.mani.feature.currency

import io.github.youndie.mani.feature.currency.data.CurrentCurrencyRepository
import io.github.youndie.mani.feature.currency.data.CurrentCurrencyRepositoryImpl
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val currencyModule = module {
    singleOf(::GetCurrentCurrencyUseCase)
    singleOf(::CurrentCurrencyRepositoryImpl).bind<CurrentCurrencyRepository>()
}
