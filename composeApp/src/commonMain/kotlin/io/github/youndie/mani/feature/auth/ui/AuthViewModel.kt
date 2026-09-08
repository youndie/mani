package io.github.youndie.mani.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.youndie.mani.feature.auth.LoginParams
import io.github.youndie.mani.feature.auth.domain.AuthUseCase
import io.github.youndie.mani.feature.auth.domain.DemoUseCase
import io.github.youndie.mani.feature.auth.ui.model.AuthUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(private val authUseCase: AuthUseCase, private val startDemoUseCase: DemoUseCase) : ViewModel() {

    private val state = MutableStateFlow(AuthUiState())
    val observe = state.asStateFlow()

    fun onUsernameChanged(username: String) {
        state.update {
            it.copy(username = username, errorMessage = null)
        }
    }

    fun onPasswordChanged(password: String) {
        state.update {
            it.copy(password = password, errorMessage = null)
        }
    }

    fun onTryDemoClicked() {
        viewModelScope.launch {
            state.update {
                it.copy(demoLoading = true, errorMessage = null)
            }

            startDemoUseCase().fold(
                onSuccess = {
                    state.update {
                        it.copy(success = true)
                    }
                },
                onFailure = { throwable ->
                    state.update {
                        it.copy(demoLoading = false, errorMessage = throwable.message.orEmpty())
                    }
                },
            )
        }
    }

    fun onLoginClicked() {
        viewModelScope.launch {
            state.update {
                it.copy(loading = true, errorMessage = null)
            }

            val result = authUseCase(LoginParams(state.value.username, state.value.password))

            result.fold(
                onSuccess = {
                    state.update {
                        it.copy(success = true)
                    }
                },
                onFailure = { throwable ->
                    state.update {
                        it.copy(loading = false, errorMessage = throwable.message.orEmpty())
                    }
                },
            )
        }
    }
}
