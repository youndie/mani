package ru.workinprogress.feature.categories.domain

import ru.workinprogress.feature.categories.data.CategoriesRepository
import ru.workinprogress.feature.transaction.Category
import ru.workinprogress.useCase.UseCase

class AddCategoryUseCase(private val repository: CategoriesRepository) : UseCase<Category, Category>() {
    override suspend operator fun invoke(params: Category): Result<Category> = withTry { repository.create(params) }
}
