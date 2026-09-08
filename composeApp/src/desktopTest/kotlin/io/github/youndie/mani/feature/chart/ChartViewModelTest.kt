package io.github.youndie.mani.feature.chart

import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import io.github.youndie.mani.defaultMinDate
import io.github.youndie.mani.feature.currency.Currency
import io.github.youndie.mani.feature.currency.GetCurrentCurrencyUseCase
import io.github.youndie.mani.feature.currency.data.CurrentCurrencyRepository
import io.github.youndie.mani.feature.transaction.Category
import io.github.youndie.mani.feature.transaction.Transaction
import io.github.youndie.mani.feature.transaction.data.FakeTransactionsRepository
import io.github.youndie.mani.feature.transaction.toChartInternal
import io.github.youndie.mani.today
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * График: те же правила, сложенные по дням.
 *
 * Проверяется здесь то, чего не видно ни в ленте, ни в прогнозе: горизонт слева обрезан, пустой
 * список не даёт кадра вовсе, а валюта берётся из настроек. Модель была переписана без теста, и
 * это долг той правки.
 *
 * Прогон в `runBlocking` с настоящим таймаутом, а не в `runTest`: поток модели уходит на
 * `Dispatchers.Default`, и виртуальное время тестового планировщика до него не достаёт — с ним
 * ожидание «ничего не пришло» завершалось бы мгновенно и проходило бы всегда.
 */
class ChartViewModelTest {

    private val currency = object : CurrentCurrencyRepository {
        override var currency = Currency.Rub
    }

    private fun viewModel(vararg rules: Transaction): ChartViewModel {
        val repository = FakeTransactionsRepository(transactions = rules.toList())

        return ChartViewModel(GetChartUseCase(repository), GetCurrentCurrencyUseCase(currency))
            .also { runBlocking { repository.load() } }
    }

    private fun rule(monthsAgo: Int) = Transaction(
        id = "1",
        amount = 100.toBigDecimal(),
        income = false,
        date = today().minus(monthsAgo, DateTimeUnit.MONTH),
        until = null,
        period = Transaction.Period.Week,
        comment = "Groceries",
        category = Category.default,
    )

    /** Пустая лента — рисовать нечего, и экран остаётся на состоянии загрузки. */
    @Test
    fun anEmptyLedgerProducesNoFrame() = runBlocking {
        val frame = withTimeoutOrNull(500) { viewModel().observe.first() }

        assertNull(frame, "пустой список отдал кадр, и экран нарисовал бы пустые оси")
    }

    /**
     * Горизонт обрезается слева.
     *
     * Правило заведено два месяца назад, а показывается месяц: без обрезки график уезжал бы
     * в прошлое настолько, насколько давно человек им пользуется.
     */
    @Test
    fun theHorizonIsTrimmedOnTheLeft() = runBlocking {
        val rule = rule(monthsAgo = 2)

        // Положительный контроль: без обрезки дни ДО среза есть, иначе проверка ниже холостая.
        assertTrue(
            listOf(rule).toChartInternal().days.keys.any { it <= defaultMinDate },
            "фикстура не даёт дней до среза — проверять нечего",
        )

        val frame = assertNotNull(withTimeoutOrNull(2_000) { viewModel(rule).observe.first() })

        assertTrue(frame.days.isNotEmpty())
        assertTrue(frame.days.keys.all { it > defaultMinDate }, "горизонт не обрезан слева")
    }

    /** Валюта берётся из настроек, а не из умолчания модели. */
    @Test
    fun theFrameCarriesTheConfiguredCurrency() = runBlocking {
        val frame = assertNotNull(withTimeoutOrNull(2_000) { viewModel(rule(monthsAgo = 1)).observe.first() })

        assertEquals(Currency.Rub, frame.currency)
    }
}
