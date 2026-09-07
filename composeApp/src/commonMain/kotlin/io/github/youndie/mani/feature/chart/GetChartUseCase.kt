package io.github.youndie.mani.feature.chart

import io.github.youndie.mani.feature.transaction.Transaction
import io.github.youndie.mani.feature.transaction.domain.TransactionRepository
import io.github.youndie.mani.feature.transaction.toChartInternal
import io.github.youndie.mani.useCase.EmptyParams
import io.github.youndie.mani.useCase.NonParameterizedUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetChartUseCase(private val transactionRepository: TransactionRepository) :
    NonParameterizedUseCase<Flow<ChartResponse>>() {

    override suspend operator fun invoke(params: EmptyParams) =
        Result.Success(transactionRepository.dataStateFlow.map(List<Transaction>::toChartInternal))
}
