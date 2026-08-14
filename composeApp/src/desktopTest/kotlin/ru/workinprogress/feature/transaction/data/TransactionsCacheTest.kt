package ru.workinprogress.feature.transaction.data

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import ru.workinprogress.feature.transaction.DataSource
import ru.workinprogress.feature.transaction.Transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val rule =
    Transaction(
        id = "1",
        amount = 1450.toBigDecimal(),
        income = false,
        date = LocalDate(2026, 8, 16),
        until = null,
        period = Transaction.Period.Month,
        comment = "Rent",
    )

private class FailingSource(
    var fail: Boolean,
) : DataSource<Transaction> {
    override suspend fun create(params: Transaction) = params

    override suspend fun load(): List<Transaction> {
        if (fail) throw IllegalStateException("no network")
        return listOf(rule)
    }

    override suspend fun update(params: Transaction) = params

    override suspend fun delete(id: String) = true
}

class TransactionsCacheTest {
    @Test
    fun `empty cache reads as nothing`() {
        assertNull(TransactionsCache(MapSettings()).load())
    }

    @Test
    fun `broken cache does not blow up`() {
        val settings = MapSettings()
        TransactionsCache(settings).save(listOf(rule))
        settings.putString("transactions.cache", "{ not json")

        // Испорченный кэш — повод сходить в сеть, а не упасть на старте.
        assertNull(TransactionsCache(settings).load())
    }

    @Test
    fun `without network the last known list is shown`() =
        runTest {
            val settings = MapSettings()
            val source = FailingSource(fail = false)
            val repository = TransactionRepositoryImpl(source, TransactionsCache(settings))

            repository.load()
            assertEquals(listOf(rule), repository.dataStateFlow.value)
            assertNull(repository.showingCacheFrom.value, "сеть была — отметки о кэше быть не должно")

            source.fail = true
            repository.load()

            assertEquals(listOf(rule), repository.dataStateFlow.value)
            assertNotNull(repository.showingCacheFrom.value, "экран должен сказать, что данные не свежие")
        }

    @Test
    fun `without network and without cache the failure surfaces`() =
        runTest {
            val repository = TransactionRepositoryImpl(FailingSource(fail = true), TransactionsCache(MapSettings()))

            // Показывать нечего и врать нечем — отказ должен дойти до экрана.
            val failed =
                try {
                    repository.load()
                    false
                } catch (e: IllegalStateException) {
                    true
                }

            assertTrue(failed)
        }
}
