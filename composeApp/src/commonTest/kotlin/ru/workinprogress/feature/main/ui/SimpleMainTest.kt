package ru.workinprogress.feature.main.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.datetime.LocalDate
import ru.workinprogress.feature.chart.ui.ChartComponent
import ru.workinprogress.feature.chart.ui.model.ChartUi
import kotlin.test.Test

class SimpleMainTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun mainLayoutTest() {
        runComposeUiTest {
            setContent {
                MainContent(
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
}
