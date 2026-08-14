package ru.workinprogress.useCase

abstract class UseCase<P, T> {
    abstract suspend operator fun invoke(params: P): Result<T>

    protected inline fun withTry(func: () -> T): Result<T> = withTry<T>(func)
    suspend fun get(params: P) = ((invoke(params)) as Result.Success<T>).data
    suspend fun getOrNull(params: P) = ((invoke(params)) as? Result.Success<T>)?.data

    sealed class Result<T> {
        class Error<T>(val throwable: Throwable) : Result<T>()
        class Success<T>(val data: T) : Result<T>()
    }
}

inline fun <T> withTry(func: () -> T): UseCase.Result<T> = try {
    UseCase.Result.Success(func())
} catch (e: Exception) {
    UseCase.Result.Error(e)
}
