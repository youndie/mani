@file:OptIn(ExperimentalCoroutinesApi::class)

package ru.workinprogress.feature.main

import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get
import ru.workinprogress.feature.auth.data.TokenRepository
import ru.workinprogress.feature.auth.data.TokenRepositoryCommon
import ru.workinprogress.feature.auth.data.TokenStorage
import ru.workinprogress.feature.auth.data.TokenStorageImpl
import ru.workinprogress.feature.auth.domain.LogoutUseCase
import ru.workinprogress.feature.categories.data.CategoriesRepository
import ru.workinprogress.feature.categories.domain.GetCategoriesUseCase
import ru.workinprogress.feature.category.FakeCategoriesDataSource
import ru.workinprogress.feature.currency.Currency
import ru.workinprogress.feature.currency.GetCurrentCurrencyUseCase
import ru.workinprogress.feature.currency.data.CurrentCurrencyRepository
import ru.workinprogress.feature.transaction.*
import ru.workinprogress.feature.transaction.data.FakeTransactionsRepository
import ru.workinprogress.feature.transaction.domain.DeleteTransactionsUseCase
import ru.workinprogress.feature.transaction.domain.GetTransactionsUseCase
import ru.workinprogress.feature.transaction.domain.TransactionRepository
import ru.workinprogress.mani.today
import kotlin.test.*
import ru.workinprogress.feature.main.ui.ForecastUiState
import ru.workinprogress.feature.demo.domain.SeedUseCase
import ru.workinprogress.useCase.EmptyParams


class MainViewModelTest : KoinTest {
    private var shouldReturnError = false
    private val targetCategory = Category("100", "Target")

    @BeforeTest
    fun setUp() {
        startKoin {
            modules(testModule({ shouldReturnError }))
        }
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @Test
    fun testLoadTransactionsSuccess() = runTest {
        val viewModel: MainViewModel = get()
        assertEquals(MainViewModel.loadingItems, viewModel.observe.value.transactions)
        while (viewModel.observe.value.loading) {
            runCurrent()
        }
        assertTrue(!viewModel.observe.value.loading)
        assertTrue(viewModel.observe.value.transactions.isNotEmpty())
        assertTrue(viewModel.observe.value.transactions.all { entry -> entry.key >= today() })

        get<TransactionRepository>().reset()
    }

    @Test
    fun testLoadTransactionsError() = runTest {
        shouldReturnError = true

        val viewModel: MainViewModel = get()
        assertEquals(MainViewModel.loadingItems, viewModel.observe.value.transactions)

        runCurrent()

        assertTrue(!viewModel.observe.value.loading)
        assertTrue(viewModel.observe.value.transactions.isEmpty())

        // Сервер не ответил и показать нечего — это состояние всего экрана, а не сообщение в
        // углу, и у него есть причина, которую можно прочитать.
        val unreachable = viewModel.observe.value.unreachable
        assertNotNull(unreachable)
        assertTrue(unreachable.cause?.contains("no response") == true, unreachable.cause)
        assertEquals(MainViewModel.RETRY_SECONDS, unreachable.retryInSeconds)

        get<TransactionRepository>().reset()
    }

    @Test
    fun testTransactionFilters() = runTest {
        val viewModel: MainViewModel = get()

        runCurrent()

        viewModel.onUpcomingToggle(false)

        runCurrent()
        assertTrue(viewModel.observe.value.transactions.none { entry -> entry.key >= today() })

        viewModel.onCategorySelected(targetCategory)
        runCurrent()
        assertTrue(
            viewModel.observe.value.transactions
                .flatMap { it.value }
                .all { item ->
                    item.category.id == targetCategory.id
                })

        get<TransactionRepository>().reset()
    }

    @Test
    fun testTransactionSelected() = runTest {
        val viewModel: MainViewModel = get()
        while (viewModel.observe.value.loading) {
            runCurrent()
        }

        val firstTransaction = viewModel.observe.value.transactions.entries.first().value.first()

        viewModel.onTransactionSelected(firstTransaction)
        runCurrent()
        assertNotNull(
            viewModel.observe.value.selectedTransactions.find { item -> item.id == firstTransaction.id }
        )

        viewModel.onTransactionSelected(firstTransaction)
        runCurrent()
        assertNull(
            viewModel.observe.value.selectedTransactions.find { item -> item.id == firstTransaction.id }
        )

        get<TransactionRepository>().reset()
    }

    @Test
    fun testTransactionDelete() = runTest {
        val viewModel: MainViewModel = get()

        while (viewModel.observe.value.loading) {
            runCurrent()
        }

        val firstTransaction = viewModel.observe.value.transactions.entries.first().value.first()

        viewModel.onTransactionSelected(firstTransaction)
        viewModel.onShowDeleteDialogClicked()
        runCurrent()
        assertTrue(viewModel.observe.value.showDeleteDialog)

        viewModel.onDismissDeleteDialog()
        runCurrent()
        assertFalse(viewModel.observe.value.showDeleteDialog)

        viewModel.onShowDeleteDialogClicked()
        runCurrent()
        assertTrue(viewModel.observe.value.showDeleteDialog)
        viewModel.onDeleteClicked()

        runCurrent()
        assertFalse(viewModel.observe.value.showDeleteDialog)
        assertTrue(viewModel.observe.value.selectedTransactions.isEmpty())

        assertNull(
            viewModel.observe.value.transactions.flatMap {
                it.value
            }.find { item -> item.id == firstTransaction.id }
        )

        assertNull(
            viewModel.observe.value.selectedTransactions.find { item -> item.id == firstTransaction.id }
        )

        get<TransactionRepository>().reset()
    }

    @Test
    fun testCloseContextMenu() = runTest {
        val viewModel: MainViewModel = get()

        while (viewModel.observe.value.loading) {
            runCurrent()
        }

        viewModel.onCategorySelected(targetCategory)
        viewModel.onContextMenuClosed()
        assertTrue(viewModel.observe.value.selectedTransactions.isEmpty())
    }

    @Test
    fun testLogout() = runTest {
        val viewModel: MainViewModel = get()

        while (viewModel.observe.value.loading) {
            runCurrent()
        }

        viewModel.onProfileClicked()
        assertTrue(viewModel.observe.value.showProfile)

        viewModel.onLogoutClicked()
        runCurrent()
        assertTrue(viewModel.observe.value.transactions.isEmpty())
    }

    @Test
    fun testProfile() = runTest {
        val viewModel: MainViewModel = get()

        while (viewModel.observe.value.loading) {
            runCurrent()
        }

        viewModel.onProfileClicked()
        assertTrue(viewModel.observe.value.showProfile)
        viewModel.onProfileDismiss()
        assertFalse(viewModel.observe.value.showProfile)
    }

    @Test
    fun forecastIsSteadyWhenBalanceNeverCrossesZero() {
        val start = LocalDate(2000, 1, 1)
        val transactions =
            listOf(Transaction("0", 100.0.toBigDecimal(), true, start, null, Transaction.Period.OneTime, ""))

        val forecast = MainViewModel.buildForecast(
            transactions.simulate(start, defaultPeriodAppend(start)),
            Currency.Usd,
            start.plus(1, DateTimeUnit.DAY)
        )

        assertEquals(ForecastUiState.Steady("100 $"), forecast)
    }

    @Test
    fun forecastShowsTheDayMoneyRunsOut() {
        val start = LocalDate(2000, 1, 1)
        val runsOut = LocalDate(2000, 1, 20)

        val transactions = listOf(
            Transaction("0", 100.0.toBigDecimal(), true, start, null, Transaction.Period.OneTime, ""),
            Transaction("1", 300.0.toBigDecimal(), false, runsOut, null, Transaction.Period.OneTime, "")
        )

        val forecast = MainViewModel.buildForecast(
            transactions.simulate(start, defaultPeriodAppend(start)),
            Currency.Usd,
            start.plus(1, DateTimeUnit.DAY)
        )

        assertEquals(
            ForecastUiState.RunsOut(
                runsOutOn = "20 January",
                daysLeft = 18,
                balanceToday = "100 $",
                // Дно совпадает с днём обнуления: после него в этих данных ничего не происходит.
                lowestPoint = "\u2212200 $",
                lowestOn = "20 January",
            ),
            forecast
        )
    }

    @Test
    fun dayBalancesAccumulateAcrossDays() {
        val start = LocalDate(2000, 1, 1)

        val transactions = listOf(
            Transaction("0", 100.0.toBigDecimal(), true, start, null, Transaction.Period.OneTime, ""),
            Transaction(
                "1", 30.0.toBigDecimal(), false,
                start.plus(2, DateTimeUnit.DAY), null, Transaction.Period.OneTime, ""
            )
        )

        val balances = MainViewModel.buildDayBalances(
            transactions.simulate(start, defaultPeriodAppend(start)),
            Currency.Usd
        )

        assertEquals("100 $", balances[start])
        // День без движений держит вчерашний остаток, иначе лента рвётся.
        assertEquals("100 $", balances[start.plus(1, DateTimeUnit.DAY)])
        assertEquals("70 $", balances[start.plus(2, DateTimeUnit.DAY)])
    }

    @Test
    fun forecastIsEmptyWithoutRules() {
        val forecast = MainViewModel.buildForecast(emptyMap(), Currency.Usd, LocalDate(2000, 1, 1))

        assertEquals(ForecastUiState.Empty, forecast)
    }

    @Test
    fun crossingAlreadyBehindIsNotAForecast() {
        val start = LocalDate(2000, 1, 1)

        val transactions = listOf(
            Transaction("0", 100.0.toBigDecimal(), true, start, null, Transaction.Period.OneTime, ""),
            Transaction(
                "1", 300.0.toBigDecimal(), false,
                start.plus(2, DateTimeUnit.DAY), null, Transaction.Period.OneTime, ""
            )
        )

        // «Деньги кончатся вчера» — не прогноз, а бессмыслица: показываем баланс.
        val forecast = MainViewModel.buildForecast(
            transactions.simulate(start, defaultPeriodAppend(start)),
            Currency.Usd,
            start.plus(10, DateTimeUnit.DAY)
        )

        assertEquals(ForecastUiState.Steady("-200 $"), forecast)
    }

    @Test
    fun runsOutDateIsTheCrossingDayNotTheRuleStart() {
        val ruleStart = LocalDate(2000, 1, 1)

        val transactions = listOf(
            Transaction("0", 300.0.toBigDecimal(), true, ruleStart, null, Transaction.Period.OneTime, ""),
            Transaction("1", 100.0.toBigDecimal(), false, ruleStart, null, Transaction.Period.Week, "")
        )

        val forecast = MainViewModel.buildForecast(
            transactions.simulate(ruleStart, defaultPeriodAppend(ruleStart)),
            Currency.Usd,
            ruleStart.plus(1, DateTimeUnit.DAY)
        )

        // Правило заведено 1 января, а знак меняется 22-го — показывать надо второе.
        assertEquals("22 January", (forecast as ForecastUiState.RunsOut).runsOutOn)
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }

    private fun testModule(withError: () -> Boolean) = module {
        single<TransactionRepository> {
            FakeTransactionsRepository(
                withError,
                listOf(
                    Transaction(
                        id = "upcoming",
                        amount = 500.0.toBigDecimal(),
                        income = true,
                        date = today().plus(1, DateTimeUnit.DAY),
                        until = null,
                        period = Transaction.Period.OneTime,
                        comment = ""
                    ),
                    Transaction(
                        id = "past",
                        amount = 250.0.toBigDecimal(),
                        income = true,
                        date = LocalDate(2000, 1, 1),
                        until = null,
                        period = Transaction.Period.OneTime,
                        comment = ""
                    ),
                    Transaction(
                        id = "past2",
                        amount = 250.0.toBigDecimal(),
                        income = true,
                        date = LocalDate(2000, 1, 1),
                        until = null,
                        period = Transaction.Period.OneTime,
                        comment = "",
                        category = targetCategory
                    )
                )
            )
        }
        single<GetTransactionsUseCase> { GetTransactionsUseCase(get()) }
        single<GetCurrentCurrencyUseCase> { GetCurrentCurrencyUseCase(get()) }
        single<CurrentCurrencyRepository> { testCurrencyRepository }
        single<DeleteTransactionsUseCase> { DeleteTransactionsUseCase(get(), Dispatchers.Unconfined) }
        single<GetCategoriesUseCase> { GetCategoriesUseCase(get()) }
        single<SeedUseCase> { FakeSeedUseCase() }
        factory<MainViewModel> { MainViewModel(get(), get(), get(), get(), get(), get(), Dispatchers.Unconfined) }

        singleOf(::TokenRepositoryCommon).bind<TokenRepository>()
        singleOf(::TokenStorageImpl).bind<TokenStorage>()
        singleOf(::LogoutUseCase)
        singleOf(::FakeCategoriesDataSource).bind<DataSource<Category>>()
        singleOf(::CategoriesRepository)
    }

}

private class FakeSeedUseCase : SeedUseCase() {
    override suspend fun invoke(params: EmptyParams): Result<Boolean> = Result.Success(true)
}
