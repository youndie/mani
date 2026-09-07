package io.github.youndie.mani.feature.transaction.domain

import io.github.youndie.mani.data.ServerException
import io.github.youndie.mani.feature.transaction.Transaction
import io.github.youndie.mani.useCase.UseCase

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
