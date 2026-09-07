package io.github.youndie.mani.feature.transaction.domain

import io.github.youndie.mani.feature.transaction.Transaction
import io.github.youndie.mani.useCase.UseCase

class UpdateTransactionUseCase(private val transactionsRepository: TransactionRepository) :
    UseCase<Transaction, Boolean>() {

    override suspend operator fun invoke(params: Transaction): Result<Boolean> = withTry {
        transactionsRepository.update(params)
    }
}
