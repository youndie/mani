package io.github.youndie.mani.feature.category

import io.github.youndie.mani.feature.transaction.Category

interface CategoryRepository {
    suspend fun getByUser(userId: String): List<Category>

    suspend fun create(category: Category, userId: String): Category

    suspend fun getById(categoryId: String): Category?

    /**
     * @param userId владелец. Категория лежит в его документе, и без этого параметра фильтр
     *   опирался бы на один лишь идентификатор категории — то есть на значение, пришедшее от
     *   клиента.
     */
    suspend fun update(category: Category, userId: String): Category

    suspend fun delete(categoryId: String)
}
