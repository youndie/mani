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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
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

    // Токен читается **однажды и без подписки**: он решает только, с какого экрана начать.
    //
    // Подписка здесь стоила дорого. Граф пересобирается на каждую перерисовку этого места, а
    // новый граф сбрасывает навигацию на свою точку входа — то есть каждый приход токена (вход,
    // демо, выход) молча перекидывал экран. Пока точка входа считалась от того же токена, это
    // выглядело как работающая навигация; стоило её зафиксировать — вход стал возвращать на
    // витрину. Переходы между входом и выходом делаются явно, ниже и в `MainComponent`.
    val startDestination = remember {
        val authorized = tokenRepository.observeToken().value.refreshToken?.isNotEmpty() == true
        if (authorized) ManiScreen.Main.name else ManiScreen.Welcome.name
    }

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
                onLoggedOut = { navController.navigateAndClean(ManiScreen.Welcome.name) },
            )
        }
        composable(ManiScreen.Welcome.name) {
            WelcomeComponent(
                appBarState,
                onSignInClicked = { navController.navigate(ManiScreen.Login.name) },
                onSignupClicked = { navController.navigate(ManiScreen.Signup.name) },
                // Тем же способом, что и вход: витрина уходит из стека, а точка входа графа
                // переезжает на главную — иначе выход потом искал бы в стеке несуществующее.
                onSuccess = { navController.navigateAndClean(ManiScreen.Main.name) },
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
