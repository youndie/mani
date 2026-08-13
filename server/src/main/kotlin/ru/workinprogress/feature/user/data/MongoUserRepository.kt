package ru.workinprogress.feature.user.data

import com.mongodb.MongoException
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import ru.workinprogress.feature.auth.LoginParams
import ru.workinprogress.feature.auth.data.hashing.HashingService
import ru.workinprogress.feature.auth.data.hashing.SaltedHash
import ru.workinprogress.feature.user.User
import ru.workinprogress.feature.user.data.UserDb.Companion.fromDb

const val USER_COLLECTION = "users"

class MongoUserRepository(
    mongoDatabase: MongoDatabase,
    private val hashingService: HashingService,
) : UserRepository {
    private val db = mongoDatabase.getCollection<UserDb>(USER_COLLECTION)

    override suspend fun save(user: LoginParams): String? {
        return try {
            val saltedHash = hashingService.generateSaltedHash(user.password)
            val result =
                db.insertOne(
                    UserDb(
                        id = ObjectId(),
                        username = user.name,
                        password = saltedHash.hash,
                        salt = saltedHash.salt,
                        tokens = emptyList(),
                        categories = emptyList(),
                    ),
                )
            result.insertedId?.asObjectId()?.value?.toHexString()
        } catch (e: MongoException) {
            logger.error("Unable to insert user", e)
            null
        }
    }

    override suspend fun findUserByCredentials(credentials: LoginParams): User? {
        val user = db.find<UserDb>(Filters.eq("username", credentials.name)).firstOrNull() ?: return null
        val matches = hashingService.verify(credentials.password, SaltedHash(user.password, user.salt.orEmpty()))
        return if (matches) user.fromDb() else null
    }

    override suspend fun findUserById(id: String): User? =
        db.find<UserDb>(Filters.eq("_id", ObjectId(id))).firstOrNull()?.fromDb()

    override suspend fun findByUsername(userName: String): User? =
        db.find<UserDb>(Filters.eq("username", userName)).firstOrNull()?.fromDb()

    // Префикс приходит константой из общего кода, не от пользователя, поэтому экранировать в
    // регулярном выражении нечего.
    override suspend fun findByUsernamePrefix(prefix: String): List<User> =
        db
            .find<UserDb>(Filters.regex("username", "^$prefix"))
            .map { it.fromDb() }
            .toList()

    override suspend fun delete(userId: String) {
        db.deleteOne(Filters.eq("_id", ObjectId(userId)))
    }

    private companion object {
        val logger = LoggerFactory.getLogger(MongoUserRepository::class.java)
    }
}

class MongoTokenRepository(
    mongoDatabase: MongoDatabase,
) : TokenRepository {
    private val db = mongoDatabase.getCollection<UserDb>(USER_COLLECTION)

    override suspend fun addToken(
        token: String,
        userId: String,
    ) {
        db.updateOne(
            Filters.eq("_id", ObjectId(userId)),
            Updates.addToSet(UserDb::tokens.name, token),
        )
    }

    override suspend fun findUserByToken(refreshToken: String): User? =
        db.find<UserDb>(Filters.eq(UserDb::tokens.name, refreshToken)).firstOrNull()?.fromDb()

    override suspend fun removeToken(
        token: String,
        userId: String,
    ) {
        db.updateOne(
            Filters.eq("_id", ObjectId(userId)),
            Updates.pull(UserDb::tokens.name, token),
        )
    }
}
