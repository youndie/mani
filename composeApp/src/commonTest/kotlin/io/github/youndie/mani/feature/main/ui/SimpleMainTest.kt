package io.github.youndie.mani.feature.main.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import io.github.youndie.mani.feature.chart.ui.ChartComponent
import io.github.youndie.mani.feature.chart.ui.model.ChartUi
import io.github.youndie.mani.feature.currency.Currency
import io.github.youndie.mani.feature.transaction.Category
import io.github.youndie.mani.feature.transaction.Transaction
import io.github.youndie.mani.feature.transaction.ui.model.TransactionUiItem
import io.github.youndie.mani.today
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.datetime.LocalDate
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
                                todayIndexProvider = { 0 },
                            ),
                        )
                    },
                )
            }
            onNodeWithTag("chartBox").assertIsDisplayed()
            onNodeWithTag("futureInfo").assertIsDisplayed()
            onNodeWithTag("filters").assertIsDisplayed()
            onNodeWithTag("transactions").assertIsDisplayed()
        }
    }

    /**
     * Сегодняшний день в ленте назван словом: это точка отсчёта, и искать её по дате — работа,
     * которую экран должен делать сам.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun todayHeaderIsNamedTest() {
        val todayItem = rent.copy(date = today())

        runComposeUiTest {
            setContent {
                MainContent(
                    transactions = persistentMapOf(today() to persistentListOf(todayItem)),
                    forecast = ForecastUiState.RunsOut("12 October", 60, "4895 $"),
                    chart = {},
                )
            }
            onNodeWithText("TODAY \u00B7", substring = true).assertIsDisplayed()
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
