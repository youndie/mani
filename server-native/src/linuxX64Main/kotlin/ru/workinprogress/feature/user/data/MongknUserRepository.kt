package ru.workinprogress.feature.user.data

import kotlinx.coroutines.flow.firstOrNull
import ru.workinprogress.feature.auth.LoginParams
import ru.workinprogress.feature.auth.data.hashing.HashingService
import ru.workinprogress.feature.auth.data.hashing.SaltedHash
import ru.workinprogress.feature.user.User
import ru.workinprogress.mani.db.USER_COLLECTION
import ru.workinprogress.mani.db.UserDb
import ru.workinprogress.mongkn.MongoDatabase
import ru.workinprogress.mongkn.bson.BsonDocument
import ru.workinprogress.mongkn.bson.BsonObjectId
import ru.workinprogress.mongkn.bson.BsonString
import ru.workinprogress.mongkn.ext.filter
import ru.workinprogress.mongkn.ext.find

class MongknUserRepository(
    mongoDatabase: MongoDatabase,
    private val hashingService: HashingService,
) : UserRepository {
    private val db = mongoDatabase.getCollection<UserDb>(USER_COLLECTION)

    override suspend fun save(user: LoginParams): String? {
        val saltedHash = hashingService.generateSaltedHash(user.password)
        val id = BsonObjectId.generate().hex

        db.insertOne(
            UserDb(
                id = id,
                username = user.name,
                password = saltedHash.hash,
                salt = saltedHash.salt,
            ),
        )
        return id
    }

    override suspend fun findUserByCredentials(credentials: LoginParams): User? {
        val user = db.find { UserDb::username eq credentials.name }.firstOrNull() ?: return null
        val matches = hashingService.verify(credentials.password, SaltedHash(user.password, user.salt.orEmpty()))
        return if (matches) user.toUser() else null
    }

    override suspend fun findUserById(id: String): User? = db.find { "_id" eq id }.firstOrNull()?.toUser()

    override suspend fun findByUsername(userName: String): User? =
        db.find { UserDb::username eq userName }.firstOrNull()?.toUser()
}

class MongknTokenRepository(
    mongoDatabase: MongoDatabase,
) : TokenRepository {
    private val db = mongoDatabase.getCollection<UserDb>(USER_COLLECTION)

    override suspend fun addToken(
        token: String,
        userId: String,
    ) {
        db.updateOne(
            filter = byId(userId),
            update = BsonDocument("\$addToSet" to BsonDocument(TOKENS to BsonString(token))),
        )
    }

    /**
     * Ищется **элемент** массива, а не массив целиком: в MongoDB `{"tokens": "…"}` по полю-массиву
     * означает «содержит такое значение». Ссылка на свойство здесь не годится — она типизирована
     * списком и закодировала бы массив из одного элемента.
     *
     * **Значение передаётся готовым [BsonString], и это не стилистика.** Строковая форма фильтра
     * кодирует значение сериализатором **поля**, а у `tokens` это `List<String>`. mongkn
     * рассчитывает на откат: при несовпадении типа ловит `ClassCastException` и кодирует
     * значение по рантайм-типу. В отладочной сборке так и происходит — в релизной
     * Kotlin/Native проверку приведения не вставляет, исключения не возникает, и
     * `CollectionSerializer` идёт итерировать строку как коллекцию. Наружу это выходит
     * `ArrayIndexOutOfBoundsException` и 500 на `/auth/refresh` — **только в релизном бинаре**,
     * то есть ровно в том, который едет в образ.
     *
     * Готовый [BsonValue] `FieldCodec` возвращает как есть, не доходя до сериализатора, поэтому
     * расхождения между сборками здесь больше нет.
     */
    override suspend fun findUserByToken(refreshToken: String): User? =
        db.find { TOKENS eq BsonString(refreshToken) }.firstOrNull()?.toUser()

    override suspend fun removeToken(
        token: String,
        userId: String,
    ) {
        db.updateOne(
            filter = byId(userId),
            update = BsonDocument("\$pull" to BsonDocument(TOKENS to BsonString(token))),
        )
    }

    private companion object {
        val TOKENS = UserDb::tokens.name
    }
}

/**
 * `_id` строкой, а не ссылкой на свойство: имя поля mongkn берёт из имени свойства Kotlin, а у
 * него стоит `@SerialName("_id")`. Строковая форма **внутри** `filter { }` всё равно кодирует
 * значение сериализатором поля, то есть уходит на сервер `ObjectId`'ом. Тот же вызов снаружи
 * блока ушёл бы строкой и не нашёл бы ничего — молча.
 */
internal fun byId(id: String) = filter<UserDb> { "_id" eq id }

internal fun UserDb.toUser() = User(id, username)
