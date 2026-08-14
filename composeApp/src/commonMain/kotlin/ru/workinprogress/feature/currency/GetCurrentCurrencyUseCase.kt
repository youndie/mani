package ru.workinprogress.feature.currency

import ru.workinprogress.feature.currency.data.CurrentCurrencyRepository
import ru.workinprogress.useCase.EmptyParams
import ru.workinprogress.useCase.NonParameterizedUseCase

class GetCurrentCurrencyUseCase(private val currencyRepository: CurrentCurrencyRepository) :
    NonParameterizedUseCase<Currency>() {

    override suspend fun invoke(params: EmptyParams): Result<Currency> = Result.Success(currencyRepository.currency)
}
