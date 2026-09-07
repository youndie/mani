package io.github.youndie.mani.feature.categories.domain

import io.github.youndie.mani.data.ServerException
import io.github.youndie.mani.feature.categories.data.CategoriesRepository
import io.github.youndie.mani.feature.transaction.Category
import io.github.youndie.mani.useCase.EmptyParams
import io.github.youndie.mani.useCase.NonParameterizedUseCase
import kotlinx.coroutines.flow.Flow

class GetCategoriesUseCase(private val repository: CategoriesRepository) :
    NonParameterizedUseCase<Flow<List<Category>>>() {

    override suspend fun invoke(params: EmptyParams): Result<Flow<List<Category>>> {
        try {
            repository.load()
        } catch (e: Exception) {
            return Result.Error(ServerException("Network Error", e))
        }

        return Result.Success(repository.dataStateFlow)
    }
}
