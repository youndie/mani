package io.github.youndie.mani.dataState

import io.github.youndie.mani.useCase.EmptyParams
import io.github.youndie.mani.useCase.NonParameterizedUseCase
import io.github.youndie.mani.useCase.UseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

sealed class DataState<T> {
    class Loading<T> : DataState<T>()
    data class Success<T>(val data: T) : DataState<T>()
    data class Error<T>(val throwable: Throwable) : DataState<T>()
}

fun <T> NonParameterizedUseCase<T>.toDataState(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): Flow<DataState<T>> = toDataState(EmptyParams, dispatcher)

fun <P, T> UseCase<P, T>.toDataState(p: P, dispatcher: CoroutineDispatcher = Dispatchers.Default): Flow<DataState<T>> =
    flow {
        emit(DataState.Loading())

        val result = withContext(dispatcher) {
            invoke(p)
        }

        when (result) {
            is UseCase.Result.Error -> {
                emit(DataState.Error(result.throwable))
            }

            is UseCase.Result.Success -> {
                emit(DataState.Success(result.data))
            }
        }
    }
