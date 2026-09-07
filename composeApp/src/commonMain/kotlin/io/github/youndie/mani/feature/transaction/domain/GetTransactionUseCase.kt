package io.github.youndie.mani.feature.transaction.domain

import io.github.youndie.mani.feature.transaction.Transaction
import io.github.youndie.mani.feature.transaction.domain.TransactionRepository
import io.github.youndie.mani.useCase.UseCase

class GetTransactionUseCase(private val transactionRepository: TransactionRepository) :
    UseCase<String, Transaction>() {

    override suspend fun invoke(params: String): Result<Transaction> = withTry { transactionRepository.getById(params) }
}
