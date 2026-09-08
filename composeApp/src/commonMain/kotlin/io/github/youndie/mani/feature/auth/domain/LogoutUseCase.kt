package io.github.youndie.mani.feature.auth.domain

import io.github.youndie.mani.feature.auth.data.TokenRepository
import io.github.youndie.mani.feature.transaction.domain.TransactionRepository
import io.github.youndie.mani.useCase.EmptyParams
import io.github.youndie.mani.useCase.NonParameterizedUseCase

class LogoutUseCase(
    private val tokenRepository: TokenRepository,
    private val transactionRepository: TransactionRepository,
) : NonParameterizedUseCase<Boolean>() {
    override suspend fun invoke(params: EmptyParams): Result<Boolean> {
        tokenRepository.set("", "")
        transactionRepository.reset()
        return Result.success(true)
    }
}
