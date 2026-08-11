package ru.workinprogress.feature.category

import ru.workinprogress.feature.transaction.Category

interface CategoryRepository {
    suspend fun getByUser(userId: String): List<Category>

    suspend fun create(
        category: Category,
        userId: String,
    ): Category

    suspend fun getById(categoryId: String): Category?

    suspend fun update(category: Category): Category

    suspend fun delete(categoryId: String)
}
