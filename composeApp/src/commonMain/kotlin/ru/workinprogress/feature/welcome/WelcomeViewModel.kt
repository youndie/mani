package ru.workinprogress.feature.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.workinprogress.feature.auth.domain.DemoUseCase
import ru.workinprogress.feature.health.domain.HealthUseCase
import ru.workinprogress.useCase.UseCase

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
            val result = healthUseCase()
            if (result is UseCase.Result.Success) {
                val health = result.data
                state.update { it.copy(server = "ktor · ${health.build} · ${health.version}") }
            }
        }
    }

    fun onTryDemoClicked() {
        viewModelScope.launch {
            state.update { it.copy(loading = true, errorMessage = null) }

            when (val result = demoUseCase()) {
                is UseCase.Result.Success -> state.update { it.copy(success = true) }

                is UseCase.Result.Error ->
                    state.update {
                        it.copy(loading = false, errorMessage = result.throwable.message.orEmpty())
                    }
            }
        }
    }
}
