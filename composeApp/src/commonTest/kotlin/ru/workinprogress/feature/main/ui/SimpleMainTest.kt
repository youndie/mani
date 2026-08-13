package ru.workinprogress.feature.main.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.datetime.LocalDate
import ru.workinprogress.feature.chart.ui.ChartComponent
import ru.workinprogress.feature.chart.ui.model.ChartUi
import ru.workinprogress.feature.currency.Currency
import ru.workinprogress.feature.transaction.Category
import ru.workinprogress.feature.transaction.Transaction
import ru.workinprogress.feature.transaction.ui.model.TransactionUiItem
import kotlin.test.Test

class SimpleMainTest {

    private val day = LocalDate(2026, 8, 16)

    private val rent = TransactionUiItem(
        Transaction(
            id = "1",
            amount = 1450.toBigDecimal(),
            income = false,
            date = day,
            until = null,
            period = Transaction.Period.Month,
            comment = "Rent",
            category = Category("1", "Home"),
        ),
        Currency.Usd,
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun mainLayoutTest() {
        runComposeUiTest {
            setContent {
                MainContent(
                    transactions = persistentMapOf(day to persistentListOf(rent)),
                    forecast = ForecastUiState.RunsOut("12 October", 60, "4895 $"),
                    chart = {
                        ChartComponent(
                            ChartUi(
                                days = persistentMapOf(LocalDate(2000, 1, 1) to 0.0.toBigDecimal()),
                                todayIndexProvider = { 0 }),
                        )
                    })
            }
            onNodeWithTag("chartBox").assertIsDisplayed()
            onNodeWithTag("futureInfo").assertIsDisplayed()
            onNodeWithTag("filters").assertIsDisplayed()
            onNodeWithTag("transactions").assertIsDisplayed()
        }
    }

    /**
     * На первом запуске фильтровать нечего: заголовок ленты и два чипа стояли над пустотой и
     * читались как недогрузившийся экран.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun emptyLayoutHasNoFiltersTest() {
        runComposeUiTest {
            setContent {
                MainContent(
                    forecast = ForecastUiState.Empty,
                    onAddFirstRule = {},
                    onFillWithDemoData = {},
                )
            }
            onNodeWithTag("filters").assertDoesNotExist()
            onNodeWithTag("forecastEmpty").assertIsDisplayed()
            onNodeWithTag("forecastPlaceholder").assertIsDisplayed()
            onNodeWithTag("addFirstRule").assertIsDisplayed()
            onNodeWithTag("fillWithDemoData").assertIsDisplayed()
        }
    }
}
