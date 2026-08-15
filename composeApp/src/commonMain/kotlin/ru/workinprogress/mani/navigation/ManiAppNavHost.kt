package ru.workinprogress.mani.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import ru.workinprogress.feature.auth.data.TokenRepository
import ru.workinprogress.feature.auth.ui.component.LoginComponent
import ru.workinprogress.feature.auth.ui.component.SignupComponent
import ru.workinprogress.feature.main.ui.MainComponent
import ru.workinprogress.feature.transaction.ui.component.AddTransactionComponent
import ru.workinprogress.feature.transaction.ui.component.EditTransactionComponent
import ru.workinprogress.feature.transaction.ui.component.TransactionsListComponent
import ru.workinprogress.feature.welcome.WelcomeComponent
import ru.workinprogress.mani.components.MainAppBarState
import kotlin.math.roundToInt

@Composable
@NonRestartableComposable
fun ManiAppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    appBarState: MainAppBarState,
    snackbarHostState: SnackbarHostState,
    onBackClicked: () -> Unit,
) {
    val tokenRepository = koinInject<TokenRepository>()
    val tokenState = tokenRepository.observeToken().collectAsStateWithLifecycle()
    val isAuth = derivedStateOf { tokenState.value.refreshToken?.isNotEmpty() == true }

    // Точка входа выбирается один раз, при первой отрисовке. Иначе она менялась прямо во время
    // перехода: «Try the demo» кладёт токен, `isAuth` переключается, граф пересобирается с другим
    // корнем — и под главным экраном оставалась лишняя запись, из-за которой появлялась стрелка
    // «назад», ведущая в никуда. Дальнейшие переходы делает навигация, а не эта строка.
    val startDestination = remember { if (isAuth.value) ManiScreen.Main.name else ManiScreen.Welcome.name }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.fillMaxSize().then(modifier),
    ) {
        composable(ManiScreen.Main.name) {
            MainComponent(
                appBarState,
                snackbarHostState,
                onTransactionClicked = { navController.navigate(TransactionRoute(it)) },
                onAddTransactionClicked = { navController.navigate(ManiScreen.Add.name) },
            )
        }
        composable(ManiScreen.Welcome.name) {
            WelcomeComponent(
                appBarState,
                onSignInClicked = { navController.navigate(ManiScreen.Login.name) },
                onSignupClicked = { navController.navigate(ManiScreen.Signup.name) },
                onSuccess = {
                    navController.navigate(ManiScreen.Main.name) {
                        popUpTo(ManiScreen.Welcome.name) { inclusive = true }
                    }
                },
            )
        }
        composable(ManiScreen.Add.name) {
            AddTransactionComponent {
                navController.popBackStack()
            }
        }
        composable(
            ManiScreen.Login.name,
        ) {
            LoginComponent(appBarState, {
                navController.navigate(ManiScreen.Signup.name)
            }) {
                navController.navigateAndClean(ManiScreen.Main.name)
            }
        }
        composable(ManiScreen.Signup.name, enterTransition = {
            slideIn(initialOffset = {
                IntOffset(
                    0,
                    (it.height / 2f).roundToInt(),
                )
            }) + fadeIn()
        }, exitTransition = {
            fadeOut() + slideOut(targetOffset = {
                IntOffset(
                    0,
                    (it.height / 2f).roundToInt(),
                )
            })
        })
        {
            SignupComponent(onBackClicked) {
                navController.navigate(ManiScreen.Login.name)
            }
        }
        composable(ManiScreen.Preload.name) {
            Box(
                modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
            )
        }
        composable(ManiScreen.History.name) {
            TransactionsListComponent(
                appBarState = appBarState,
                onTransactionClicked = {
                    navController.navigate(TransactionRoute(it))
                },
            )
        }

        composable<TransactionRoute> {
            val transaction = it.toRoute<TransactionRoute>()
            EditTransactionComponent(transaction) {
                navController.popBackStack()
            }
        }
    }
}

@Serializable
class TransactionRoute(val id: String)
