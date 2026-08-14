package ru.workinprogress.mani

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoClient
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.mongo.transitions.Mongod
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess
import de.flapdoodle.reverse.TransitionWalker
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.bson.types.ObjectId
import org.junit.Test
import org.koin.core.context.GlobalContext.stopKoin
import ru.workinprogress.feature.category.CategoryRepository
import ru.workinprogress.feature.category.data.MongoCategoryRepository
import ru.workinprogress.feature.transaction.Category
import ru.workinprogress.feature.transaction.data.CategoryDb
import ru.workinprogress.feature.user.data.UserDb
import ru.workinprogress.mani.db.deleteById
import kotlin.test.*

class MongoCategoryRepositoryTest {

    lateinit var running: TransitionWalker.ReachedState<RunningMongodProcess>
    private val url get() = "mongodb://${running.current().serverAddress}"
    private val databaseName = "test"
    private val collectionName = "users"
    private val client by lazy { MongoClient.create(url) }
    private val db by lazy { client.getDatabase(databaseName) }
    private val collection get() = db.getCollection<UserDb>(collectionName)
    private val categoryRepository: CategoryRepository by lazy { MongoCategoryRepository(db) }
    private val testUser = UserDb(ObjectId(), "test", "", "", emptyList(), emptyList())

    @BeforeTest
    fun setup() {
        running = Mongod.instance().start(Version.V8_0_3)

        runBlocking {
            db.createCollection(collectionName)
        }
    }

    private fun dbTest(
        before: suspend () -> Unit = {
            collection.insertOne(testUser)
        },
        after: suspend () -> Unit = {
            collection.deleteById(testUser.id.toHexString())
        },
        test: suspend () -> Unit,
    ) {
        runBlocking {
            try {
                before()

                test()
            } catch (e: Throwable) {
                println("Error: ${e.message}")
                throw e
            }
            after()
        }
    }

    @Test
    fun `Category create test`() {
        dbTest {
            val category = Category("Test", "Create")
            categoryRepository.create(category, testUser.id.toHexString())

            val user = collection.find(
                Filters.eq("_id", testUser.id),
            ).firstOrNull()

            assertNotNull(user)
            assertTrue(
                user.categories.orEmpty()
                    .any {
                        it.name == category.name
                    },
            )
        }
    }

    @Test
    fun `Category read test`() {
        dbTest {
            val category = Category("", "Read")
            val categoryDb = CategoryDb(ObjectId(), category.name)

            collection.findOneAndUpdate(
                Filters.eq("_id", testUser.id),
                Updates.addToSet(UserDb::categories.name, categoryDb),
            )

            assertEquals(
                category.name,
                categoryRepository.getById(categoryDb.id.toHexString())?.name,
            )

            assertEquals(
                category.name,
                categoryRepository.getByUser(testUser.id.toHexString()).firstOrNull()?.name,
            )
        }
    }

    @Test
    fun `Category update test`() {
        dbTest {
            val category = Category("", "Create")
            val categoryDb = CategoryDb(ObjectId(), category.name)
            val updatedDb = categoryDb.copy(name = "Update")
            val updated = Category(updatedDb.id.toHexString(), updatedDb.name)

            collection.findOneAndUpdate(
                Filters.eq("_id", testUser.id),
                Updates.addToSet(UserDb::categories.name, categoryDb),
            )

            categoryRepository.update(updated)
            val actualUser = collection.find(Filters.eq("_id", testUser.id)).firstOrNull()
            val actualCategory = actualUser?.categories?.firstOrNull()

            assertEquals(updated.name, actualCategory?.name)
        }
    }

    @Test
    fun `Category delete test`() {
        dbTest {
            val category = Category("", "Create")
            val categoryDb = CategoryDb(ObjectId(), category.name)

            collection.findOneAndUpdate(
                Filters.eq("_id", testUser.id),
                Updates.addToSet(UserDb::categories.name, categoryDb),
            )

            assertNotNull(collection.find(Filters.eq("_id", testUser.id)).firstOrNull()?.categories?.firstOrNull())

            categoryRepository.delete(categoryDb.id.toHexString())

            assertNull(collection.find(Filters.eq("_id", testUser.id)).firstOrNull()?.categories?.firstOrNull())
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }
}
