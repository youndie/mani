package io.github.youndie.mani.feature.transaction.domain

import io.github.youndie.mani.data.ServerException
import io.github.youndie.mani.feature.transaction.Transaction
import io.github.youndie.mani.useCase.UseCase
import io.github.youndie.mani.utilz.suspendRunCatching

class AddTransactionUseCase(private val transactionsRepository: TransactionRepository) :
    UseCase<Transaction, Boolean>() {

    override suspend operator fun invoke(params: Transaction): Result<Boolean> = suspendRunCatching {
        transactionsRepository.create(params)
        true
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(ServerException(message = "Network Error")) },
    )
}
