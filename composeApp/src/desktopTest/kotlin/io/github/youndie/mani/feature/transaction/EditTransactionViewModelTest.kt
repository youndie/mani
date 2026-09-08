package io.github.youndie.mani.feature.transaction

import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import io.github.youndie.mani.feature.categories.data.CategoriesRepository
import io.github.youndie.mani.feature.categories.domain.AddCategoryUseCase
import io.github.youndie.mani.feature.categories.domain.DeleteCategoryUseCase
import io.github.youndie.mani.feature.categories.domain.ObserveCategoriesUseCase
import io.github.youndie.mani.feature.category.FakeCategoriesDataSource
import io.github.youndie.mani.feature.currency.Currency
import io.github.youndie.mani.feature.currency.GetCurrentCurrencyUseCase
import io.github.youndie.mani.feature.currency.data.CurrentCurrencyRepository
import io.github.youndie.mani.feature.transaction.data.FakeTransactionsRepository
import io.github.youndie.mani.feature.transaction.domain.GetTransactionUseCase
import io.github.youndie.mani.feature.transaction.domain.ObserveTransactionsUseCase
import io.github.youndie.mani.feature.transaction.domain.UpdateTransactionUseCase
import io.github.youndie.mani.feature.transaction.ui.EditTransactionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Правка существующего правила: открыть по идентификатору, изменить, сохранить.
 *
 * Весь этот путь не проверялся ничем — соседний набор тринадцать раз собирает форму создания и ни
 * разу форму правки. Между ними разница не в одном поле: правка начинается с загрузки по
 * идентификатору, а он приходит из ссылки и может оказаться устаревшим.
 *
 * Зависимости собираются руками, а не через Koin: их восемь, и явный список читается лучше, чем
 * модуль, из которого надо выяснять, что чем подменено.
 */
class EditTransactionViewModelTest {

    private val rule = Transaction(
        id = "rent",
        amount = 1450.toBigDecimal(),
        income = false,
        date = LocalDate(2026, 8, 16),
        until = null,
        period = Transaction.Period.Month,
        comment = "Rent",
        category = Category.default,
    )

    private val transactions = FakeTransactionsRepository(transactions = listOf(rule))
    private val categories = CategoriesRepository(FakeCategoriesDataSource())
    private val currency = object : CurrentCurrencyRepository {
        override var currency = Currency.Usd
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun viewModel(id: String): EditTransactionViewModel {
        transactions.load()

        return EditTransactionViewModel(
            transactionId = id,
            getTransactionUseCase = GetTransactionUseCase(transactions),
            updateTransactionUseCase = UpdateTransactionUseCase(transactions),
            addCategoryUseCase = AddCategoryUseCase(categories),
            observeCategoriesUseCase = ObserveCategoriesUseCase(categories),
            getCurrentCurrencyUseCase = GetCurrentCurrencyUseCase(currency),
            deleteCategoryUseCase = DeleteCategoryUseCase(categories, transactions),
            observeTransactionsUseCase = ObserveTransactionsUseCase(transactions),
            dispatcher = Dispatchers.Unconfined,
        )
    }

    @Test
    fun theRuleIsLoadedIntoTheForm() = runTest {
        val viewModel = viewModel(rule.id)
        runCurrent()

        val state = viewModel.observe.value
        assertEquals("1450", state.amount)
        assertEquals("Rent", state.comment)
        assertEquals(Transaction.Period.Month, state.period)
        assertEquals(rule.date, state.date.value)
        assertTrue(state.edit, "форма правки не отличима от формы создания")
    }

    /**
     * Устаревший идентификатор — сообщение на форме, а не падение.
     *
     * Ссылку на правило можно открыть, когда правила уже нет. Раньше значение читалось через
     * `get()`, который бросает отказ прямо в корутину модели, и экран просто умирал.
     */
    @Test
    fun aStaleIdBecomesAMessageOnTheForm() = runTest {
        val viewModel = viewModel("no-such-rule")
        runCurrent()

        val state = viewModel.observe.value
        assertNotNull(state.errorMessage, "правило не найдено, а форма промолчала")
        assertFalse(state.loading, "форма осталась в загрузке навсегда")
    }

    @Test
    fun savingWritesTheChangeAndClosesTheForm() = runTest {
        val viewModel = viewModel(rule.id)
        runCurrent()

        viewModel.onAmountChanged("1500")
        viewModel.onSubmitClicked()
        runCurrent()

        assertTrue(viewModel.observe.value.success, "форма не закрылась после сохранения")
        assertEquals(
            "1500",
            transactions.dataStateFlow.value.single { it.id == rule.id }.amount.toPlainString(),
            "изменение не доехало до хранилища",
        )
    }
}
