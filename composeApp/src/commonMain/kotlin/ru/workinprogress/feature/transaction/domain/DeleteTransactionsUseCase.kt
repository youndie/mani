package ru.workinprogress.feature.transaction.domain

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import ru.workinprogress.feature.transaction.domain.TransactionRepository
import ru.workinprogress.useCase.UseCase

class DeleteTransactionsUseCase(
    private val repository: TransactionRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(4),
) : UseCase<List<String>, Boolean>() {

    override suspend fun invoke(params: List<String>): Result<Boolean> {
        val supervisor = SupervisorJob()

        params.map { transactionId ->
            CoroutineScope(supervisor + dispatcher).async {
                repository.delete(transactionId)
            }
        }.awaitAll()

        return Result.Success(true)
    }
}
