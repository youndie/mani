package ru.workinprogress.useCase

abstract class NonParameterizedUseCase<T> : UseCase<EmptyParams, T>() {
    suspend operator fun invoke() = invoke(EmptyParams)
    suspend fun get() = ((invoke()) as Result.Success<T>).data
}
