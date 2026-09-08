package io.github.youndie.mani.feature.categories.domain

import io.github.youndie.mani.feature.categories.data.CategoriesRepository
import io.github.youndie.mani.feature.transaction.Category
import io.github.youndie.mani.useCase.UseCase
import io.github.youndie.mani.utilz.suspendRunCatching

class AddCategoryUseCase(private val repository: CategoriesRepository) : UseCase<Category, Category>() {
    override suspend operator fun invoke(params: Category): Result<Category> = suspendRunCatching {
        repository.create(params)
    }
}
