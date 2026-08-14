package ru.workinprogress.uiState

import kotlinx.coroutines.flow.MutableStateFlow

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

inline fun <reified T : LoadingState> MutableStateFlow<T>.showLoading() {
    value = value.load() as T
}

inline fun <reified T : ErrorState> MutableStateFlow<T>.showError(message: String) {
    value = value.showError(message) as T
}

inline fun <T, reified R : DataState<T>> MutableStateFlow<R>.showData(data: T) {
    value = value.showData(data) as R
}

interface CommonUiState<T> :
    DataState<T>,
    LoadingState,
    ErrorState
