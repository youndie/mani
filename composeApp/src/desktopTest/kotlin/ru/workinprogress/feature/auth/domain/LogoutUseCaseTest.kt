package ru.workinprogress.feature.transaction.ui.ru.workinprogress.feature.auth.domain

import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import ru.workinprogress.feature.auth.data.TokenRepository
import ru.workinprogress.feature.auth.data.TokenRepositoryCommon
import ru.workinprogress.feature.auth.data.TokenStorageImpl
import ru.workinprogress.feature.auth.domain.LogoutUseCase
import ru.workinprogress.feature.transaction.Category
import ru.workinprogress.feature.transaction.Transaction
import ru.workinprogress.feature.transaction.data.FakeTransactionsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogoutUseCaseTest {
    @Test
    fun logoutTest() = runTest {
        val transactionRepository = FakeTransactionsRepository()
        val tokenRepository: TokenRepository = TokenRepositoryCommon(TokenStorageImpl())
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
