package io.github.youndie.mani.feature.category.data

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.github.youndie.mani.feature.category.CategoryRepository
import io.github.youndie.mani.feature.transaction.Category
import io.github.youndie.mani.feature.transaction.data.CategoryDb
import io.github.youndie.mani.feature.user.data.USER_COLLECTION
import io.github.youndie.mani.feature.user.data.UserDb
import kotlinx.coroutines.flow.firstOrNull
import org.bson.types.ObjectId

/**
 * Категории лежат массивом в документе пользователя, поэтому коллекция здесь — `users`.
 */
class MongoCategoryRepository(mongoDatabase: MongoDatabase) : CategoryRepository {
    private val db = mongoDatabase.getCollection<UserDb>(USER_COLLECTION)

    override suspend fun getByUser(userId: String): List<Category> =
        getUserById(userId)?.categories?.map { it.toCategory() }.orEmpty()

    override suspend fun create(category: Category, userId: String): Category {
        val newCategory = CategoryDb(ObjectId(), category.name)

        db.findOneAndUpdate(
            Filters.eq("_id", ObjectId(userId)),
            Updates.addToSet(UserDb::categories.name, newCategory),
        )

        return newCategory.toCategory()
    }

    override suspend fun getById(categoryId: String): Category? = getUserByCategoryId(categoryId)
        ?.categories
        ?.find { it.id.toHexString() == categoryId }
        ?.toCategory()

    /**
     * Владелец — часть фильтра, а не только проверка в маршруте: тот же второй рубеж, что и у
     * транзакций. Оба условия лежат в одном документе, а не в `$and`, потому что позиционный
     * `$` в обновлении требует, чтобы поле массива было названо самим запросом.
     */
    override suspend fun update(category: Category, userId: String): Category {
        db.findOneAndUpdate(
            Filters.and(
                Filters.eq("_id", ObjectId(userId)),
                Filters.eq("categories._id", ObjectId(category.id)),
            ),
            Updates.set("categories.$.name", category.name),
        )
        return category
    }

    override suspend fun delete(categoryId: String) {
        val user = getUserByCategoryId(categoryId) ?: return
        val category = user.categories?.find { it.id.toHexString() == categoryId } ?: return

        db.updateOne(
            Filters.eq("_id", user.id),
            Updates.pull(UserDb::categories.name, category),
        )
    }

    private suspend fun getUserById(userId: String) = db.find<UserDb>(Filters.eq("_id", ObjectId(userId))).firstOrNull()

    private suspend fun getUserByCategoryId(categoryId: String) =
        db.find<UserDb>(Filters.eq("categories._id", ObjectId(categoryId))).firstOrNull()

    private fun CategoryDb.toCategory() = Category(id.toHexString(), name)
}
