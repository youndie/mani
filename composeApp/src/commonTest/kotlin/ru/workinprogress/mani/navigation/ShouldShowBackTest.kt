package ru.workinprogress.mani.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Стрелка «назад» на корневом экране обещала возврат, которого нет.
 *
 * После «Try the demo» под главным экраном оставалась запись в стеке — граф пересобирался, когда
 * появлялся токен, — и стрелка появлялась. Нажатие на неё не уводило никуда: экран оставался тем
 * же, исчезала только сама стрелка.
 */
class ShouldShowBackTest {

    @Test
    fun rootScreensNeverShowBack() {
        listOf(ManiScreen.Main, ManiScreen.Welcome, ManiScreen.Preload).forEach { screen ->
            assertFalse(shouldShowBack(screen, hasPrevious = true), "$screen обещает возврат, которого нет")
        }
    }

    @Test
    fun innerScreensShowBackWhenThereIsSomewhereToReturn() {
        listOf(ManiScreen.Add, ManiScreen.Transaction, ManiScreen.History).forEach { screen ->
            assertTrue(shouldShowBack(screen, hasPrevious = true), "$screen оставляет человека без выхода")
            assertFalse(shouldShowBack(screen, hasPrevious = false))
        }
    }
}
