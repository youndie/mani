package io.github.youndie.mani.uiState

interface LoadingState {
    val loading: Boolean

    fun load(): LoadingState
}

interface ErrorState {
    val errorMessage: String?

    fun showError(message: String): ErrorState
}

interface DataState<T> {
    val data: T

    fun showData(data: T): DataState<T>
}

interface CommonUiState<T> :
    DataState<T>,
    LoadingState,
    ErrorState
