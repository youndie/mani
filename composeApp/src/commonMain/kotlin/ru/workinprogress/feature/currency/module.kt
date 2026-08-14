package ru.workinprogress.feature.currency

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import ru.workinprogress.feature.currency.data.CurrentCurrencyRepository
import ru.workinprogress.feature.currency.data.CurrentCurrencyRepositoryImpl

val currencyModule = module {
    singleOf(::GetCurrentCurrencyUseCase)
    singleOf(::CurrentCurrencyRepositoryImpl).bind<CurrentCurrencyRepository>()
}
