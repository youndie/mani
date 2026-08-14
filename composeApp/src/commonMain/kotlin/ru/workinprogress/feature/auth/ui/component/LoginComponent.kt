package ru.workinprogress.feature.auth.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.module.rememberKoinModules
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module
import ru.workinprogress.feature.auth.domain.AuthUseCase
import ru.workinprogress.feature.auth.domain.LoginUseCase
import ru.workinprogress.feature.auth.ui.AuthViewModel
import ru.workinprogress.feature.auth.ui.model.AuthComponentUiState
import ru.workinprogress.mani.components.LoadingButton
import ru.workinprogress.mani.components.MainAppBarState

@Composable
fun LoginComponent(appBarState: MainAppBarState, onSignupClicked: () -> Unit, onSuccess: () -> Unit) {
    rememberKoinModules {
        listOf(
            module {
                singleOf(::LoginUseCase).bind<AuthUseCase>()
                viewModelOf(::AuthViewModel)
            },
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    appBarState.disable()
                }

                Lifecycle.Event.ON_STOP -> {
                }

                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val viewModel = koinViewModel<AuthViewModel>()
    val state = viewModel.observe.collectAsStateWithLifecycle()

    LaunchedEffect(state.value.success) {
        if (state.value.success) {
            onSuccess()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 32.dp, bottom = 24.dp),
    ) {
        AuthComponentImpl(
            Modifier.weight(1f, true),
            AuthComponentUiState(
                "Mani",
                state.value.username,
                state.value.password,
                "Login",
                state.value.errorMessage,
                state.value.loading,
            ),
            onUsernameChanged = viewModel::onUsernameChanged,
            onPasswordChanged = viewModel::onPasswordChanged,
            onButtonClicked = viewModel::onLoginClicked,
        )

        // Главная дорожка витрины: посетителю нечего вводить, песочницу заводит сервер.
        LoadingButton(
            loading = state.value.demoLoading,
            buttonText = "Try the demo",
            modifier = Modifier.testTag("tryDemo"),
            onButtonClicked = viewModel::onTryDemoClicked,
        )

        Text(
            "your own sandbox — no account, no password",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = 4.dp),
        )

        TextButton(onSignupClicked) {
            Text("Sign up")
        }
    }
}
