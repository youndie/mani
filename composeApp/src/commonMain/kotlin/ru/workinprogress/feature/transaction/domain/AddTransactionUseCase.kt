package ru.workinprogress.feature.transaction.domain

import ru.workinprogress.feature.transaction.Transaction
import ru.workinprogress.mani.data.ServerException
import ru.workinprogress.useCase.UseCase

class AddTransactionUseCase(private val transactionsRepository: TransactionRepository) :
    UseCase<Transaction, Boolean>() {

    override suspend operator fun invoke(params: Transaction): Result<Boolean> = withTry {
        transactionsRepository.create(params)
        true
    }.let { result ->
        return when (result) {
            is Result.Error<*> -> {
                Result.Error(ServerException(message = "Network Error"))
            }

            else -> result
        }
    }
}
