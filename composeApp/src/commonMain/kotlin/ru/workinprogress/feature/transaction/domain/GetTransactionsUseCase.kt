package ru.workinprogress.feature.transaction.domain

import kotlinx.coroutines.flow.Flow
import io.ktor.client.plugins.ResponseException
import ru.workinprogress.mani.data.ServerException
import ru.workinprogress.feature.transaction.Transaction
import ru.workinprogress.useCase.EmptyParams
import ru.workinprogress.useCase.NonParameterizedUseCase
import kotlin.time.Instant
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.flow.StateFlow

class GetTransactionsUseCase(
    private val transactionRepository: TransactionRepository,
) : NonParameterizedUseCase<Flow<List<Transaction>>>() {

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

