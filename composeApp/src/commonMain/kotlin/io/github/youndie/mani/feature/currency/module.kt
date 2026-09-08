package io.github.youndie.mani.feature.currency

import io.github.youndie.mani.feature.currency.data.CurrentCurrencyRepository
import io.github.youndie.mani.feature.currency.data.CurrentCurrencyRepositoryImpl
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val currencyModule = module {
    singleOf(::GetCurrentCurrencyUseCase)
    // Явный вызов, а не `singleOf`: тот резолвит КАЖДЫЙ параметр конструктора из графа, включая
    // тот, у которого есть умолчание, — и модуль перестал бы собираться там, где `Settings`
    // никто не объявлял.
    single<CurrentCurrencyRepository> { CurrentCurrencyRepositoryImpl() }
}
