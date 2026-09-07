package io.github.youndie.mani.feature.categories.domain

import io.github.youndie.mani.feature.categories.data.CategoriesRepository

class ObserveCategoriesUseCase(private val categoriesRepository: CategoriesRepository) {
    val observe = categoriesRepository.dataStateFlow
}
