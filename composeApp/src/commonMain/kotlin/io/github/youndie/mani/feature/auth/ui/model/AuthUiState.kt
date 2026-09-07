package io.github.youndie.mani.feature.auth.ui.model

data class AuthUiState(
    val username: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val demoLoading: Boolean = false,
    val success: Boolean = false,
    val errorMessage: String? = null,
)
