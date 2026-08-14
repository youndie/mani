package ru.workinprogress.feature.categories.domain

import ru.workinprogress.feature.categories.data.CategoriesRepository
import ru.workinprogress.feature.transaction.Category
import ru.workinprogress.feature.transaction.domain.TransactionRepository
import ru.workinprogress.useCase.UseCase

class DeleteCategoryUseCase(
    private val repository: CategoriesRepository,
    private val transactionRepository: TransactionRepository,
) : UseCase<Category, Boolean>() {
    override suspend fun invoke(params: Category): Result<Boolean> = withTry {
        repository.delete(params.id)
    }.also {
        transactionRepository.load()
    }
}
