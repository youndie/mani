package ru.workinprogress.feature.categories.domain

import ru.workinprogress.feature.categories.data.CategoriesRepository

class ObserveCategoriesUseCase(private val categoriesRepository: CategoriesRepository) {
    val observe = categoriesRepository.dataStateFlow
}
