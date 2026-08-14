package ru.workinprogress.feature.transaction.domain

import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import ru.workinprogress.feature.transaction.Transaction
import ru.workinprogress.mani.data.ServerException
import ru.workinprogress.useCase.EmptyParams
import ru.workinprogress.useCase.NonParameterizedUseCase
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class GetTransactionsUseCase(private val transactionRepository: TransactionRepository) :
    NonParameterizedUseCase<Flow<List<Transaction>>>() {

    /** Проброшено из репозитория: экрану нужно знать, что данные последние известные, а не свежие. */
    @OptIn(ExperimentalTime::class)
    val showingCacheFrom: StateFlow<Instant?> get() = transactionRepository.showingCacheFrom

    override suspend fun invoke(params: EmptyParams): Result<Flow<List<Transaction>>> {
        try {
            transactionRepository.load()
        } catch (e: Exception) {
            return Result.Error(ServerException("Network Error", e, (e as? ResponseException)?.response?.status?.value))
        }

        return Result.Success(transactionRepository.dataStateFlow)
    }
}
