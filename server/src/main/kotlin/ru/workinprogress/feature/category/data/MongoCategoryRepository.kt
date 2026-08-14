package ru.workinprogress.feature.category.data

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import org.bson.types.ObjectId
import ru.workinprogress.feature.category.CategoryRepository
import ru.workinprogress.feature.transaction.Category
import ru.workinprogress.feature.transaction.data.CategoryDb
import ru.workinprogress.feature.user.data.USER_COLLECTION
import ru.workinprogress.feature.user.data.UserDb

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

    override suspend fun update(category: Category): Category {
        db.findOneAndUpdate(
            Filters.eq("categories._id", ObjectId(category.id)),
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
