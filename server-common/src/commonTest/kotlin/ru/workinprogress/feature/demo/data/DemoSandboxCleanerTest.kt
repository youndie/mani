package ru.workinprogress.feature.demo.data

import kotlinx.coroutines.test.runTest
import ru.workinprogress.feature.auth.LoginParams
import ru.workinprogress.feature.transaction.Transaction
import ru.workinprogress.feature.transaction.data.TransactionRecord
import ru.workinprogress.feature.transaction.data.TransactionRepository
import ru.workinprogress.feature.user.User
import ru.workinprogress.feature.user.data.UserRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class DemoSandboxCleanerTest {
    private val now = Instant.fromEpochSeconds(1_800_000_000)

    /** Идентификатор ObjectId'ом, где первые четыре байта — заданное время создания. */
    private fun objectId(createdAt: Instant): String = createdAt.epochSeconds
        .toString(16)
        .padStart(8, '0') + "0123456789abcdef"

    private fun user(name: String, createdAt: Instant) = User(id = objectId(createdAt), username = name)

    @Test
    fun `expired sandbox goes away with its transactions`() = runTest {
        val expired = user("demo-dead", now - DAY * 2)
        val users = FakeUserRepository(listOf(expired))
        val transactions = FakeTransactionRepository()

        DemoSandboxCleaner(users, transactions).sweep(now)

        assertTrue(users.stored.isEmpty())
        assertEquals(listOf(expired.id), transactions.deletedForUsers)
    }

    @Test
    fun `fresh sandbox stays`() = runTest {
        val fresh = user("demo-alive", now - HOUR)
        val users = FakeUserRepository(listOf(fresh))
        val transactions = FakeTransactionRepository()

        DemoSandboxCleaner(users, transactions).sweep(now)

        assertEquals(listOf(fresh), users.stored)
        assertTrue(transactions.deletedForUsers.isEmpty())
    }

    @Test
    fun `only sandboxes are searched`() = runTest {
        val users = FakeUserRepository(emptyList())

        DemoSandboxCleaner(users, FakeTransactionRepository()).sweep(now)

        assertEquals(DEMO_USERNAME_PREFIX, users.searchedPrefix)
    }

    @Test
    fun `id that is not an ObjectId is left alone`() = runTest {
        // Возраст такой записи неизвестен, и удалять её по догадке нельзя: чужой формат
        // ключа означает, что документ завёл не этот код.
        val alien = User(id = "not-an-object-id", username = "demo-alien")
        val users = FakeUserRepository(listOf(alien))

        DemoSandboxCleaner(users, FakeTransactionRepository()).sweep(now)

        assertEquals(listOf(alien), users.stored)
    }

    @Test
    fun `creation time is read out of the identifier`() {
        val created = now - DAY
        assertEquals(created.epochSeconds, objectId(created).objectIdCreatedAtSeconds())
        assertNull("not-an-object-id".objectIdCreatedAtSeconds())
        assertNull("ZZZZZZZZ0123456789abcdef".objectIdCreatedAtSeconds())
    }

    private companion object {
        val HOUR = 1.hours
        val DAY = 24.hours
    }
}

private class FakeUserRepository(initial: List<User>) : UserRepository {
    val stored = initial.toMutableList()
    var searchedPrefix: String? = null

    override suspend fun save(user: LoginParams): String? = null

    override suspend fun findUserByCredentials(credentials: LoginParams): User? = null

    override suspend fun findUserById(id: String): User? = stored.find { it.id == id }

    override suspend fun findByUsername(userName: String): User? = stored.find { it.username == userName }

    override suspend fun findByUsernamePrefix(prefix: String): List<User> {
        searchedPrefix = prefix
        return stored.filter { it.username.startsWith(prefix) }
    }

    override suspend fun delete(userId: String) {
        stored.removeAll { it.id == userId }
    }
}

private class FakeTransactionRepository : TransactionRepository {
    val deletedForUsers = mutableListOf<String>()

    override suspend fun create(transaction: Transaction, userId: String): String = ""

    override suspend fun getByUser(userId: String): List<TransactionRecord> = emptyList()

    override suspend fun getById(id: String): TransactionRecord? = null

    override suspend fun update(transaction: Transaction, userId: String) = Unit

    override suspend fun delete(id: String): Boolean = false

    override suspend fun deleteByUser(userId: String) {
        deletedForUsers += userId
    }
}
