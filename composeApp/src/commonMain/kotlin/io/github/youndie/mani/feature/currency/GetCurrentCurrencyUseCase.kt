package io.github.youndie.mani.feature.currency

import io.github.youndie.mani.feature.currency.data.CurrentCurrencyRepository
import io.github.youndie.mani.useCase.EmptyParams
import io.github.youndie.mani.useCase.NonParameterizedUseCase

class GetCurrentCurrencyUseCase(private val currencyRepository: CurrentCurrencyRepository) :
    NonParameterizedUseCase<Currency>() {

    override suspend fun invoke(params: EmptyParams): Result<Currency> = Result.success(currencyRepository.currency)
}
