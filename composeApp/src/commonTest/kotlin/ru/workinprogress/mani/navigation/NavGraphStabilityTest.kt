@file:OptIn(ExperimentalTestApi::class)

package ru.workinprogress.mani.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Граф собирается один раз и переживает перерисовку экрана вокруг.
 *
 * Обычный `NavHost` держит граф через `remember`, среди ключей которого — лямбда-строитель. Она
 * захватывает всё, что видит вокруг, поэтому новой становится при каждой перерисовке родителя, и
 * граф собирается заново. Установка нового графа сбрасывает навигацию на точку входа: в
 * приложении вход уводил на главную, а первая же перерисовка полосы заголовка возвращала на
 * витрину.
 *
 * Проверяется именно сборка, а не сам переход: перехода в этой среде не сделать — `NavHost`
 * требует владельца жизненного цикла, которого в тестовом окружении нет.
 */
class NavGraphStabilityTest {

    @Test
    fun graphIsBuiltOnce() = runComposeUiTest {
        var builds = 0
        var tick by mutableStateOf(0)

        setContent {
            val controller = rememberNavController()

            // Чтение состояния до узла навигации: его изменение перерисовывает это место — ровно
            // то, что делает в приложении полоса заголовка, меняя свой заголовок.
            Text("tick $tick")

            StableNavHost(navController = controller, startDestination = "welcome") {
                builds++
                composable("welcome") { Text("welcome screen") }
                composable("main") { Text("main screen") }
            }
        }

        assertEquals(1, builds)

        tick++
        waitForIdle()
        tick++
        waitForIdle()

        assertEquals(1, builds, "граф пересобрался — навигация сбросится на точку входа")
    }
}
