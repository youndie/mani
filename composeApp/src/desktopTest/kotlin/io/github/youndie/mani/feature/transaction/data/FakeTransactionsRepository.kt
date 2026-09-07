package io.github.youndie.mani.feature.transaction.data

import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import io.github.youndie.mani.feature.transaction.Transaction
import io.github.youndie.mani.feature.transaction.domain.TransactionRepository
import io.github.youndie.mani.feature.transaction.toDelete
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate

class FakeTransactionsRepository(
    private val shouldCrash: () -> Boolean = { false },
    private val transactions: List<Transaction> = listOf(
        Transaction(
            "",
            500.0.toBigDecimal(),
            true,
            LocalDate(2000, 1, 1),
            null,
            Transaction.Period.OneTime,
            "",
        ),
        toDelete,
    ),
) : TransactionRepository {

    private val data = MutableStateFlow(emptyList<Transaction>())

    override val dataStateFlow: StateFlow<List<Transaction>> = data

    override suspend fun load() {
        if (shouldCrash()) throw RuntimeException("fake")
        data.value = transactions
    }

    override fun getById(transactionId: String): Transaction = data.value.first { it.id == transactionId }

    override suspend fun create(params: Transaction): Transaction {
        if (shouldCrash()) throw RuntimeException("fake")
        data.value += params
        return params
    }

    override suspend fun update(params: Transaction): Boolean {
        data.value = data.value - getById(params.id) + params
        return true
    }

    override suspend fun delete(transactionId: String): Boolean {
        data.value -= getById(transactionId)
        return true
    }

    override fun reset() {
        data.value = emptyList()
    }

    @OptIn(kotlin.time.ExperimentalTime::class)
    override val showingCacheFrom =
        kotlinx.coroutines.flow.MutableStateFlow<kotlin.time.Instant?>(null)
}
