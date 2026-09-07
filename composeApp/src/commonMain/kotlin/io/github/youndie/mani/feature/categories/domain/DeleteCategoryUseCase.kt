package io.github.youndie.mani.feature.categories.domain

import io.github.youndie.mani.feature.categories.data.CategoriesRepository
import io.github.youndie.mani.feature.transaction.Category
import io.github.youndie.mani.feature.transaction.domain.TransactionRepository
import io.github.youndie.mani.useCase.UseCase

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
