package io.github.youndie.mani

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.youndie.mani.components.MainAppBarState
import io.github.youndie.mani.components.ManiAppBar
import io.github.youndie.mani.feature.transaction.Transaction
import io.github.youndie.mani.feature.transaction.ui.model.stringResource
import io.github.youndie.mani.navigation.ManiAppNavHost
import io.github.youndie.mani.navigation.ManiScreen
import io.github.youndie.mani.navigation.shouldShowBack
import io.github.youndie.mani.navigation.title
import io.github.youndie.mani.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.KoinApplication
import org.koin.core.module.Module
import kotlin.math.roundToInt

@Composable
@Preview
fun App(
    modifier: Modifier = Modifier,
    platformModules: List<Module> = emptyList(),
    navController: NavHostController = rememberNavController(),
    onNavHostReady: suspend (NavController) -> Unit = {},
    onBackClicked: () -> Unit = {
        navController.popBackStack()
    },
) {
    LaunchedEffect(Unit) {
        onNavHostReady(navController)
    }

    KoinApplication({
        modules(appModules + platformModules)
    }) {
        val keyboardController = LocalSoftwareKeyboardController.current

        AppTheme {
            ManiApp(
                modifier,
                navController,
                onBackClicked = {
                    keyboardController?.hide()
                    onBackClicked()
                },
            )
        }
    }
}

@Composable
fun ManiApp(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    appBarState: MainAppBarState = remember { MainAppBarState() },
    snackBarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onBackClicked: () -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()

    @Suppress(
        "ktlint:kapkan:swallowed-failure",
        "неизвестный маршрут — экран по умолчанию: навигация не должна падать",
    )
    val currentScreen = try {
        ManiScreen.valueOf(backStackEntry?.destination?.route ?: ManiScreen.Preload.name)
    } catch (e: Exception) {
        ManiScreen.Transaction
    }

    val labels = Transaction.Period.entries.map { period -> stringResource(period.stringResource) }

    LaunchedEffect(backStackEntry) {
        appBarState.showBack.value = shouldShowBack(currentScreen, navController.previousBackStackEntry != null)
        appBarState.closeContextMenu()
    }

    LaunchedEffect(currentScreen) {
        appBarState.title.value = currentScreen.title()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column(modifier = Modifier.animateContentSize()) {
                ManiAppBar(appBarState) {
                    onBackClicked()
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackBarHostState) },
        floatingActionButton = {
            AnimatedVisibility(
                currentScreen == ManiScreen.Main,
                exit = fadeOut() + slideOut(targetOffset = {
                    IntOffset(
                        0,
                        (it.height / 2f).roundToInt(),
                    )
                }),
                enter = fadeIn() + slideIn(initialOffset = {
                    IntOffset(
                        0,
                        (it.height / 2f).roundToInt(),
                    )
                }),
            ) {
                AddRuleFab(
                    onClick = { navController.navigate(ManiScreen.Add.name) },
                    modifier = Modifier.navigationBarsPadding(),
                )
            }
        },
    ) { padding ->
        ManiAppNavHost(
            modifier = Modifier.consumeWindowInsets(padding).padding(top = padding.calculateTopPadding()),
            navController = navController,
            appBarState = appBarState,
            snackbarHostState = snackBarHostState,
            onBackClicked,
        )
    }
}

/**
 * Кнопка «завести правило».
 *
 * Скруглённый квадрат, а не круг, и цвет контейнера, а не основной: в макете она держит ту же
 * форму, что карточки и чипы, и не спорит яркостью с линией прогноза — на график смотрят чаще,
 * чем нажимают на кнопку.
 */
@Composable
fun AddRuleFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.testTag("fab"),
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = "Add",
        )
    }
}
