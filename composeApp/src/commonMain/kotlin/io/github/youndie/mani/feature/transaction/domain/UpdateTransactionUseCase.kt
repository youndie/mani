package io.github.youndie.mani.feature.transaction.domain

import io.github.youndie.mani.feature.transaction.Transaction
import io.github.youndie.mani.useCase.UseCase
import io.github.youndie.mani.utilz.suspendRunCatching

class UpdateTransactionUseCase(private val transactionsRepository: TransactionRepository) :
    UseCase<Transaction, Boolean>() {

    override suspend operator fun invoke(params: Transaction): Result<Boolean> = suspendRunCatching {
        transactionsRepository.update(params)
    }
}
