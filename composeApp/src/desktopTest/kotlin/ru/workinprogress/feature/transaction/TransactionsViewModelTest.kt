package ru.workinprogress.feature.transaction

import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDate
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get
import ru.workinprogress.feature.currency.Currency
import ru.workinprogress.feature.currency.GetCurrentCurrencyUseCase
import ru.workinprogress.feature.currency.data.CurrentCurrencyRepository
import ru.workinprogress.feature.transaction.data.FakeTransactionsRepository
import ru.workinprogress.feature.transaction.domain.DeleteTransactionsUseCase
import ru.workinprogress.feature.transaction.domain.GetTransactionsUseCase
import ru.workinprogress.feature.transaction.domain.TransactionRepository
import ru.workinprogress.feature.transaction.ui.TransactionsViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

val testCurrencyRepository = object : CurrentCurrencyRepository {
    override var currency = Currency.Usd
}

private fun testModule(withError: Boolean = false) = module {
    single<TransactionRepository> { FakeTransactionsRepository({ withError }) }
    single<GetTransactionsUseCase> { GetTransactionsUseCase(get()) }
    single<GetCurrentCurrencyUseCase> { GetCurrentCurrencyUseCase(get()) }
    single<CurrentCurrencyRepository> { testCurrencyRepository }
    single<DeleteTransactionsUseCase> { DeleteTransactionsUseCase(get()) }
    single<TransactionsViewModel> { TransactionsViewModel(get(), get(), get()) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelTest : KoinTest {

    private lateinit var viewModel: TransactionsViewModel

    @BeforeTest
    fun setUp() {
        // Start Koin
        startKoin {
            modules(testModule(false))
        }
        viewModel = get()
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @Test
    fun testLoadTransactions() = runTest {
        while (viewModel.observe.value.data.isEmpty()) {
            runCurrent()
        }

        assertTrue(viewModel.observe.value.errorMessage == null)
        assertTrue(
            viewModel.observe.value.data.isNotEmpty(),
            "Expected transactions to be fetched but got an empty list.",
        )
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelErrorTest : KoinTest {

    private lateinit var viewModel: TransactionsViewModel

    @BeforeTest
    fun setUp() {
        // Start Koin
        startKoin {
            modules(testModule(true))
        }
        viewModel = get()
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @Test
    fun testLoadTransactionsFailed() = runTest {
        while (viewModel.observe.value.loading) {
            runCurrent()
        }

        assertTrue(viewModel.observe.value.errorMessage == "Network Error")
        assertTrue(
            viewModel.observe.value.data.isEmpty(),
            "Expected an empty list due to API failure but got ${viewModel.observe.value.data.size} items.",
        )
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }
}

val toDelete = Transaction(
    "ru.workinprogress.feature.main.ui.toDelete",
    500.0.toBigDecimal(),
    true,
    LocalDate(2000, 1, 1),
    null,
    Transaction.Period.OneTime,
    "",
)
