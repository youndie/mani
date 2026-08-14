package ru.workinprogress.feature.auth.ui.model

data class AuthComponentUiState(
    val title: String,
    val username: String,
    val password: String,
    val buttonText: String,
    val errorMessage: String? = null,
    val loading: Boolean,
)
