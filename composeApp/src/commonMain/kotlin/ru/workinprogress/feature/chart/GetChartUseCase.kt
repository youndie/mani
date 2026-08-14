package ru.workinprogress.feature.chart

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.workinprogress.feature.transaction.Transaction
import ru.workinprogress.feature.transaction.domain.TransactionRepository
import ru.workinprogress.feature.transaction.toChartInternal
import ru.workinprogress.useCase.EmptyParams
import ru.workinprogress.useCase.NonParameterizedUseCase

class GetChartUseCase(private val transactionRepository: TransactionRepository) :
    NonParameterizedUseCase<Flow<ChartResponse>>() {

    override suspend operator fun invoke(params: EmptyParams) =
        Result.Success(transactionRepository.dataStateFlow.map(List<Transaction>::toChartInternal))
}
