package ru.workinprogress.feature.categories.domain

import kotlinx.coroutines.flow.Flow
import ru.workinprogress.feature.categories.data.CategoriesRepository
import ru.workinprogress.feature.transaction.Category
import ru.workinprogress.mani.data.ServerException
import ru.workinprogress.useCase.EmptyParams
import ru.workinprogress.useCase.NonParameterizedUseCase

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
