@file:OptIn(ExperimentalTestApi::class)

package ru.workinprogress.feature.main.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.*
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import ru.workinprogress.feature.chart.ui.ChartComponent
import ru.workinprogress.feature.chart.ui.model.ChartUi
import ru.workinprogress.feature.transaction.Category
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Живёт в `desktopTest`, а не в общих тестах, — намеренно.
 *
 * В браузере он падал через раз на общем раннере: выпадающее меню въезжает анимацией в своём окне,
 * и кадр с ним не всегда успевает. Ожидание я поднимал дважды, до пяти секунд, — не помогло, а
 * наружу wasm отдаёт пустое «Error», по которому причину не установить. Локально в настоящем
 * Chrome тест проходит подряд сколько угодно раз, то есть проверяет он не то, что ломается.
 *
 * Логика фильтров одна на все платформы, и на десктопе она проверяется без этой лотереи. Если
 * взаимодействие с popup в браузере когда-нибудь станет предсказуемым — тест стоит вернуть.
 */
class FiltersTest {

    /**
     * Ждать появления, а не проверять сразу.
     *
     * Выпадающее меню живёт в отдельном окне и въезжает анимацией: на медленном headless-браузере
     * очередной кадр не успевал до проверки, и тест падал через раз — на разных ветках, включая
     * те, что меню вообще не трогали.
     */
    private fun ComposeUiTest.awaitText(text: String) {
        // Пять секунд вместо секунды по умолчанию: в headless-браузере на общем раннере кадр с
        // раскрытым меню не всегда успевает за секунду, и тест падал безо всякого сообщения —
        // wasm отдаёт наружу пустое «Error».
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText(text).assertIsDisplayed()
    }

    @Test
    fun filterChipsTest() = runComposeUiTest {
        val targetCategory = Category("1", "Test1")
        val stateFlow = MutableStateFlow(
            FiltersState(
                categories = persistentSetOf(Category("0", "Test0"), targetCategory),
                loading = true,
            ),
        )

        setContent {
            val state = stateFlow.collectAsState()

            FiltersChips(
                filtersState = state.value,
                onUpcomingToggle = {
                    stateFlow.update { state ->
                        state.copy(upcoming = it)
                    }
                },
            ) {
                stateFlow.update { state ->
                    state.copy(category = it)
                }
            }
        }

        onNodeWithTag("filtersShimmer").assertIsDisplayed()

        stateFlow.update { state ->
            state.copy(loading = false)
        }
        awaitText("Upcoming")
        onNodeWithText("All categories").assertIsDisplayed()

        onNodeWithText("Upcoming").performClick()
        awaitText("Past")

        onNodeWithText("Past").performClick()
        assertFalse(stateFlow.value.upcoming)
        onNodeWithText("All categories").performClick()
        awaitText(targetCategory.name)
        onNodeWithText(targetCategory.name).performClick()
        assertEquals(targetCategory, stateFlow.value.category)
    }
}
