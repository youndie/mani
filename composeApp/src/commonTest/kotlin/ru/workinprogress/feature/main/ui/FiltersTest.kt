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
 * Своя граница ожидания вместо стандартной.
 *
 * У `waitUntil` она равна секунде, и на загруженном headless-раннере wasm со Skia в секунду
 * укладывается не всегда — поэтому ожидание ниже флак приглушило, но не убрало. Настоящая
 * поломка всё равно валит тест по этой границе, и заметно раньше, чем karma сочтёт браузер
 * зависшим.
 */
private const val AWAIT_TIMEOUT_MILLIS = 5_000L

class FiltersTest {

    /**
     * Ждать появления, а не проверять сразу.
     *
     * Выпадающее меню живёт в отдельном окне и въезжает анимацией: на медленном headless-браузере
     * очередной кадр не успевал до проверки, и тест падал через раз — на разных ветках, включая
     * те, что меню вообще не трогали.
     *
     * Узел возвращается, чтобы нажимать по тому же ожиданию: «дождались и сразу нажали» одним
     * выражением не оставляет места обращению, забывшему подождать.
     */
    private fun ComposeUiTest.awaitNode(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        waitUntil(timeoutMillis = AWAIT_TIMEOUT_MILLIS) {
            onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
        return onNode(matcher).apply { assertIsDisplayed() }
    }

    private fun ComposeUiTest.awaitText(text: String) = awaitNode(hasText(text))

    private fun ComposeUiTest.awaitTag(tag: String) = awaitNode(hasTestTag(tag))

    /**
     * Состояние меняет обработчик нажатия, а не само нажатие. Читать флаг сразу после клика —
     * значит требовать, чтобы обработчик успел в тот же кадр, а на медленном браузере он
     * успевает не всегда.
     */
    private fun ComposeUiTest.awaitState(condition: () -> Boolean) {
        waitUntil(timeoutMillis = AWAIT_TIMEOUT_MILLIS, condition = condition)
    }

    // ВРЕМЕННО, вместе с джобой flake-probe: браузерный раннер теряет сообщение исключения и
    // печатает голое `Error`, поэтому вытаскиваем его изнутри теста в консоль — karma её
    // пробрасывает в лог сборки. Убрать, когда причина падения будет известна.
    @Test
    fun filterChipsTest() = runComposeUiTest {
        try {
            filterChips()
        } catch (error: Throwable) {
            println("FILTERS_TEST_FAILURE ${error::class.simpleName}: ${error.message}")
            println(error.stackTraceToString())
            throw error
        }
    }

    private suspend fun ComposeUiTest.filterChips() {
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

        awaitTag("filtersShimmer")

        stateFlow.update { state ->
            state.copy(loading = false)
        }

        awaitText("Upcoming")
        awaitText("All categories")

        awaitText("Upcoming").performClick()
        awaitText("Past").performClick()
        awaitState { !stateFlow.value.upcoming }
        assertFalse(stateFlow.value.upcoming)

        awaitText("All categories").performClick()
        awaitText(targetCategory.name).performClick()
        awaitState { stateFlow.value.category == targetCategory }
        assertEquals(targetCategory, stateFlow.value.category)
    }
}
