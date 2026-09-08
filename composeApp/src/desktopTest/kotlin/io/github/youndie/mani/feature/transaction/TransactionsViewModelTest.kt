package io.github.youndie.mani.feature.transaction

import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import io.github.youndie.mani.feature.currency.Currency
import io.github.youndie.mani.feature.currency.GetCurrentCurrencyUseCase
import io.github.youndie.mani.feature.currency.data.CurrentCurrencyRepository
import io.github.youndie.mani.feature.transaction.data.FakeTransactionsRepository
import io.github.youndie.mani.feature.transaction.domain.DeleteTransactionsUseCase
import io.github.youndie.mani.feature.transaction.domain.GetTransactionsUseCase
import io.github.youndie.mani.feature.transaction.domain.TransactionRepository
import io.github.youndie.mani.feature.transaction.ui.TransactionsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDate
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

val testCurrencyRepository = object : CurrentCurrencyRepository {
    override var currency = Currency.Usd
}

private fun testModule(repository: TransactionRepository) = module {
    single<TransactionRepository> { repository }
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
        // Диспетчер подменяется ДО построения модели: её `init` уже уходит в `viewModelScope`,
        // то есть на Main, и построенная раньше подмены модель стартует на настоящем.
        Dispatchers.setMain(StandardTestDispatcher())

        startKoin {
            modules(testModule(FakeTransactionsRepository()))
        }
        viewModel = get()
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
        // Диспетчер подменяется ДО построения модели: её `init` уже уходит в `viewModelScope`,
        // то есть на Main, и построенная раньше подмены модель стартует на настоящем.
        Dispatchers.setMain(StandardTestDispatcher())

        startKoin {
            modules(testModule(FakeTransactionsRepository({ true })))
        }
        viewModel = get()
    }

    /**
     * Отказ сети — состояние всего экрана, как на главной.
     *
     * Раньше история показывала строку «Network Error» поверх пустого списка и больше ничего не
     * пыталась: пустой список читается как «правил нет», хотя правила на месте, а пропала связь.
     */
    @Test
    fun testLoadTransactionsFailed() = runTest {
        while (viewModel.observe.value.loading) {
            runCurrent()
        }

        val unreachable = assertNotNull(viewModel.observe.value.unreachable, "история промолчала об отказе")
        assertNotNull(
            unreachable.cause,
            "причина не названа: по коду и адресу человек отличает свою сеть от чужого сервера",
        )
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

/**
 * Показанное может быть последним известным, а не свежим, и история обязана это сказать.
 *
 * Кэш подставляет репозиторий: сеть спрашивается всегда, сохранённое идёт в ход, только если она
 * отказала. Без отметки времени экран выдавал вчерашние данные за сегодняшние — и на главной
 * такая отметка была, а здесь нет.
 */
@OptIn(ExperimentalCoroutinesApi::class, kotlin.time.ExperimentalTime::class)
class TransactionsViewModelCacheTest : KoinTest {

    private val repository = FakeTransactionsRepository()
    private lateinit var viewModel: TransactionsViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        repository.showingCacheFrom.value = kotlin.time.Instant.fromEpochSeconds(1_800_000_000)
        startKoin { modules(testModule(repository)) }
        viewModel = get()
    }

    @Test
    fun cachedDataSaysWhenItWasTaken() = runTest {
        while (viewModel.observe.value.data.isEmpty()) {
            runCurrent()
        }

        assertNotNull(
            viewModel.observe.value.showingCacheFrom,
            "история выдала последние известные данные за свежие",
        )
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }
}

val toDelete = Transaction(
    "io.github.youndie.mani.feature.main.ui.toDelete",
    500.0.toBigDecimal(),
    true,
    LocalDate(2000, 1, 1),
    null,
    Transaction.Period.OneTime,
    "",
)
