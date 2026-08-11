package ru.workinprogress.feature.category.data

import kotlinx.coroutines.flow.firstOrNull
import ru.workinprogress.feature.category.CategoryRepository
import ru.workinprogress.feature.transaction.Category
import ru.workinprogress.feature.user.data.byId
import ru.workinprogress.mani.db.CategoryDb
import ru.workinprogress.mani.db.USER_COLLECTION
import ru.workinprogress.mani.db.UserDb
import ru.workinprogress.mongkn.MongoDatabase
import ru.workinprogress.mongkn.bson.BsonDocument
import ru.workinprogress.mongkn.bson.BsonObjectId
import ru.workinprogress.mongkn.bson.BsonString
import ru.workinprogress.mongkn.bson.encodeToBsonValue
import ru.workinprogress.mongkn.ext.filter
import ru.workinprogress.mongkn.ext.find

/**
 * Категории лежат массивом в документе пользователя, поэтому коллекция здесь — `users`.
 */
class MongknCategoryRepository(
    mongoDatabase: MongoDatabase,
) : CategoryRepository {
    private val db = mongoDatabase.getCollection<UserDb>(USER_COLLECTION)

    override suspend fun getByUser(userId: String): List<Category> =
        db
            .find(byId(userId))
            .firstOrNull()
            ?.categories
            ?.map { it.toCategory() }
            .orEmpty()

    override suspend fun create(
        category: Category,
        userId: String,
    ): Category {
        val newCategory = CategoryDb(id = BsonObjectId.generate().hex, name = category.name)

        db.updateOne(
            filter = byId(userId),
            // Вложенный документ кодируется **сериализатором**, а не собирается руками:
            // у `CategoryDb._id` свой сериализатор, и собранный из BsonString документ лёг бы
            // в массив с чужим типом ключа. Найти такую категорию потом было бы нечем.
            update =
                BsonDocument(
                    "\$addToSet" to
                        BsonDocument(
                            CATEGORIES to encodeToBsonValue(CategoryDb.serializer(), newCategory),
                        ),
                ),
        )

        return newCategory.toCategory()
    }

    override suspend fun getById(categoryId: String): Category? =
        findUserByCategoryId(categoryId)
            ?.categories
            ?.find { it.id == categoryId }
            ?.toCategory()

    override suspend fun update(category: Category): Category {
        db.updateOne(
            filter = byCategoryId(category.id),
            update = BsonDocument("\$set" to BsonDocument("categories.\$.name" to BsonString(category.name))),
        )
        return category
    }

    /**
     * `$pull` по условию `{_id: …}`, а не по документу целиком: MongoDB применяет вложенный
     * документ как условие к каждому элементу массива, и удаление перестаёт зависеть от того,
     * совпало ли имя. JVM-сборка передавала элемент целиком и на переименованной категории
     * молча ничего не удаляла.
     */
    override suspend fun delete(categoryId: String) {
        db.updateOne(
            filter = byCategoryId(categoryId),
            update =
                BsonDocument(
                    "\$pull" to
                        BsonDocument(
                            CATEGORIES to BsonDocument("_id" to BsonObjectId.parse(categoryId)),
                        ),
                ),
        )
    }

    private suspend fun findUserByCategoryId(categoryId: String) = db.find(byCategoryId(categoryId)).firstOrNull()

    /**
     * Путь `categories._id` — составной, и класса с таким полем не существует, поэтому
     * сериализатор поля по нему не найдётся. Кодируем значение сами: без `BsonObjectId` фильтр
     * ушёл бы строкой и не совпал бы ни с одной категорией.
     */
    private fun byCategoryId(categoryId: String) =
        filter<UserDb> { "categories._id" eq BsonObjectId.parse(categoryId) }

    private fun CategoryDb.toCategory() = Category(id, name)

    private companion object {
        val CATEGORIES = UserDb::categories.name
    }
}
