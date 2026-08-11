package ru.workinprogress.mani

import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import ru.workinprogress.feature.auth.LoginParams
import ru.workinprogress.feature.auth.data.hashing.Sha256HashingService
import ru.workinprogress.feature.category.data.MongknCategoryRepository
import ru.workinprogress.feature.transaction.Category
import ru.workinprogress.feature.transaction.Transaction
import ru.workinprogress.feature.transaction.data.MongknTransactionRepository
import ru.workinprogress.feature.user.data.MongknTokenRepository
import ru.workinprogress.feature.user.data.MongknUserRepository
import ru.workinprogress.mani.db.TRANSACTION_COLLECTION
import ru.workinprogress.mani.db.USER_COLLECTION
import ru.workinprogress.mongkn.MongoClient
import ru.workinprogress.mongkn.MongoDatabase
import ru.workinprogress.mongkn.bson.BsonDecimal128
import ru.workinprogress.mongkn.bson.BsonDocument
import ru.workinprogress.mongkn.bson.BsonObjectId
import ru.workinprogress.mongkn.bson.BsonString
import ru.workinprogress.mongkn.bson.Document
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Хранилище против настоящего mongod.
 *
 * Половина проверок здесь смотрит не на возвращённое значение, а на **сырой документ**: тип
 * `_id` и тип `amount` — это и есть совместимость с JVM-сборкой, а отличить `ObjectId` от строки
 * по результату `find` невозможно, пока обе сборки не встретятся на одной базе.
 */
class MongknStorageTest {
    private val client: MongoClient = TestMongo.client()
    private val database: MongoDatabase = client.getDatabase(TestMongo.uniqueDatabaseName("storage"))

    private val hashing = Sha256HashingService()
    private val users = MongknUserRepository(database, hashing)
    private val tokens = MongknTokenRepository(database)
    private val categories = MongknCategoryRepository(database)
    private val transactions = MongknTransactionRepository(database)

    private val rawUsers = database.getCollection(USER_COLLECTION)
    private val rawTransactions = database.getCollection(TRANSACTION_COLLECTION)

    @AfterTest
    fun tearDown() =
        runTest {
            database.drop()
            client.close()
        }

    @Test
    fun `user id lands in mongo as ObjectId`() =
        runTest {
            val id = assertNotNull(users.save(LoginParams("vasya", "hunter2")))

            val raw = assertNotNull(rawUsers.find(BsonDocument("_id" to BsonObjectId.parse(id))).firstOrNull())
            assertTrue(raw["_id"] is BsonObjectId, "_id должен быть ObjectId, а не ${raw["_id"]}")

            // Обратный ход: фильтр строкой по тому же значению обязан находить документ —
            // именно это ломалось молча, когда сериализатор поля не применялся.
            assertEquals("vasya", users.findUserById(id)?.username)
        }

    @Test
    fun `password is stored hashed`() =
        runTest {
            val id = assertNotNull(users.save(LoginParams("petya", "hunter2")))

            val raw = assertNotNull(rawUsers.find(BsonDocument("_id" to BsonObjectId.parse(id))).firstOrNull())
            val stored = (raw["password"] as BsonString).value

            assertFalse(stored == "hunter2", "пароль лежит открытым текстом")
            assertEquals(64, stored.length, "sha256 в hex")
            assertNotNull(raw["salt"])

            assertNotNull(users.findUserByCredentials(LoginParams("petya", "hunter2")))
            assertNull(users.findUserByCredentials(LoginParams("petya", "wrong")))
        }

    @Test
    fun `refresh tokens are added and removed`() =
        runTest {
            val id = assertNotNull(users.save(LoginParams("tokenized", "pass")))

            tokens.addToken("token-a", id)
            tokens.addToken("token-b", id)

            assertEquals("tokenized", tokens.findUserByToken("token-a")?.username)
            assertEquals("tokenized", tokens.findUserByToken("token-b")?.username)

            tokens.removeToken("token-a", id)

            assertNull(tokens.findUserByToken("token-a"))
            assertNotNull(tokens.findUserByToken("token-b"))
        }

    @Test
    fun `categories live inside the user document`() =
        runTest {
            val userId = assertNotNull(users.save(LoginParams("cats", "pass")))

            val created = categories.create(Category("", "Еда"), userId)

            val raw = assertNotNull(rawUsers.find(BsonDocument("_id" to BsonObjectId.parse(userId))).firstOrNull())
            val embedded = (raw["categories"] as ru.workinprogress.mongkn.bson.BsonArray)[0] as Document
            assertTrue(embedded["_id"] is BsonObjectId, "_id категории должен быть ObjectId")

            assertEquals(listOf("Еда"), categories.getByUser(userId).map { it.name })
            assertEquals("Еда", categories.getById(created.id)?.name)

            categories.update(created.copy(name = "Продукты"))
            assertEquals(listOf("Продукты"), categories.getByUser(userId).map { it.name })

            categories.delete(created.id)
            assertEquals(emptyList(), categories.getByUser(userId))
        }

    @Test
    fun `transaction amount lands in mongo as decimal128`() =
        runTest {
            val userId = assertNotNull(users.save(LoginParams("money", "pass")))
            val category = categories.create(Category("", "Еда"), userId)

            val id =
                transactions.create(
                    Transaction(
                        id = "",
                        amount = "1234.56".toBigDecimal(),
                        income = false,
                        date = LocalDate.parse("2026-08-11"),
                        period = Transaction.Period.OneTime,
                        until = null,
                        comment = "обед",
                        category = category,
                    ),
                    userId,
                )

            val raw = assertNotNull(rawTransactions.find(BsonDocument("_id" to BsonObjectId.parse(id))).firstOrNull())
            assertTrue(raw["amount"] is BsonDecimal128, "amount должен быть decimal128, а не ${raw["amount"]}")

            val record = assertNotNull(transactions.getById(id))
            assertEquals("1234.56", record.amount.toPlainString())
            assertEquals(category.id, record.categoryId)
            assertEquals(userId, record.userId)
        }

    @Test
    fun `transaction is updated and deleted`() =
        runTest {
            val userId = assertNotNull(users.save(LoginParams("crud", "pass")))
            val transaction =
                Transaction(
                    id = "",
                    amount = "10".toBigDecimal(),
                    income = true,
                    date = LocalDate.parse("2026-01-01"),
                    period = Transaction.Period.OneTime,
                    until = null,
                    comment = "было",
                    category = Category.default,
                )

            val id = transactions.create(transaction, userId)
            transactions.update(transaction.copy(id = id, comment = "стало"), userId)

            assertEquals("стало", transactions.getById(id)?.comment)
            assertEquals(1, transactions.getByUser(userId).size)

            assertTrue(transactions.delete(id))
            assertNull(transactions.getById(id))
            assertFalse(transactions.delete(id), "повторное удаление ничего не удаляет")
        }
}
