package io.github.youndie.mani.feature.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.youndie.mani.feature.auth.domain.DemoUseCase
import io.github.youndie.mani.feature.health.domain.HealthUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WelcomeUiState(
    val loading: Boolean = false,
    val success: Boolean = false,
    val errorMessage: String? = null,
    /** «ktor · kotlin/native · 1.4.2» — пусто, пока сервер не ответил. */
    val server: String? = null,
)

class WelcomeViewModel(private val demoUseCase: DemoUseCase, private val healthUseCase: HealthUseCase) : ViewModel() {
    private val state = MutableStateFlow(WelcomeUiState())
    val observe = state.asStateFlow()

    init {
        viewModelScope.launch {
            // Отказ здесь молчаливый: строка о сборке — украшение витрины, и её отсутствие не
            // должно мешать войти в демо.
            healthUseCase().onSuccess { health ->
                state.update { it.copy(server = "ktor · ${health.build} · ${health.version}") }
            }
        }
    }

    fun onTryDemoClicked() {
        viewModelScope.launch {
            state.update { it.copy(loading = true, errorMessage = null) }

            demoUseCase().fold(
                onSuccess = { state.update { it.copy(success = true) } },
                onFailure = { throwable ->
                    state.update {
                        it.copy(loading = false, errorMessage = throwable.message.orEmpty())
                    }
                },
            )
        }
    }
}
