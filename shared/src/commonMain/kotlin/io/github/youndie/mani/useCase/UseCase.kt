package io.github.youndie.mani.useCase

abstract class UseCase<P, T> {
    abstract suspend operator fun invoke(params: P): Result<T>

    suspend fun get(params: P) = invoke(params).getOrThrow()
    suspend fun getOrNull(params: P) = invoke(params).getOrNull()
}
