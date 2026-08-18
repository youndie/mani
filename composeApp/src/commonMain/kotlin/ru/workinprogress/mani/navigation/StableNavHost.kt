package ru.workinprogress.mani.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.createGraph

/**
 * `NavHost`, чей граф собирается один раз.
 *
 * Обычный `NavHost` пересобирает граф, когда меняется лямбда-строитель, а установка нового графа
 * сбрасывает навигацию на точку входа. Лямбда захватывает всё, что видит вокруг, поэтому новой
 * она становится при любой перерисовке родителя — например когда полоса заголовка меняет свой
 * заголовок. В приложении это выглядело так: вход уводил на главную, а следующая же перерисовка
 * возвращала на витрину.
 *
 * Экраны внутри при этом перерисовываются как обычно: заморожен граф, а не их содержимое.
 */
@Composable
fun StableNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
    builder: NavGraphBuilder.() -> Unit,
) {
    val graph = remember { navController.createGraph(startDestination = startDestination, builder = builder) }

    NavHost(navController = navController, graph = graph, modifier = modifier)
}
