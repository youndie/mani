package io.github.youndie.mani.feature.transaction.ui.io.github.youndie.mani.feature.auth.domain

import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import io.github.youndie.mani.feature.auth.data.TokenRepository
import io.github.youndie.mani.feature.auth.data.TokenRepositoryCommon
import io.github.youndie.mani.feature.auth.data.TokenStorageImpl
import io.github.youndie.mani.feature.auth.data.temporarySessionFile
import io.github.youndie.mani.feature.auth.domain.LogoutUseCase
import io.github.youndie.mani.feature.transaction.Category
import io.github.youndie.mani.feature.transaction.Transaction
import io.github.youndie.mani.feature.transaction.data.FakeTransactionsRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogoutUseCaseTest {
    @Test
    fun logoutTest() = runTest {
        val transactionRepository = FakeTransactionsRepository()
        val tokenRepository: TokenRepository = TokenRepositoryCommon(TokenStorageImpl(temporarySessionFile()))
        val logoutUseCase = LogoutUseCase(tokenRepository, transactionRepository)
        transactionRepository.create(
            Transaction(
                id = "0",
                amount = 0.0.toBigDecimal(),
                income = true,
                date = LocalDate(2000, 1, 1),
                until = null,
                period = Transaction.Period.OneTime,
                comment = "",
                category = Category.default,
            ),
        )
        tokenRepository.set("PRESET", "TOKEN")

        assertEquals(tokenRepository.getToken().accessToken, "PRESET")
        assertEquals(tokenRepository.getToken().refreshToken, "TOKEN")
        assertTrue(transactionRepository.dataStateFlow.value.size == 1)

        logoutUseCase.invoke()

        assertTrue(tokenRepository.getToken().accessToken.isEmpty())
        assertTrue(tokenRepository.getToken().refreshToken?.isEmpty() == true)
        assertTrue(transactionRepository.dataStateFlow.value.isEmpty())
    }
}
