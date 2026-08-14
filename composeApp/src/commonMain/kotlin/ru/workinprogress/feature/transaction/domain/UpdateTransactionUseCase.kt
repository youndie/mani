package ru.workinprogress.feature.transaction.domain

import ru.workinprogress.feature.transaction.Transaction
import ru.workinprogress.useCase.UseCase

class UpdateTransactionUseCase(private val transactionsRepository: TransactionRepository) :
    UseCase<Transaction, Boolean>() {

    override suspend operator fun invoke(params: Transaction): Result<Boolean> = withTry {
        transactionsRepository.update(params)
    }
}
