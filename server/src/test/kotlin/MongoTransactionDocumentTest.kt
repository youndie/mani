package io.github.youndie.mani

import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.MongoClient
import io.github.youndie.mani.feature.transaction.Transaction
import io.github.youndie.mani.feature.transaction.data.TRANSACTION_COLLECTION
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.firstOrNull
import org.bson.Document
import org.bson.types.Decimal128
import org.bson.types.ObjectId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Форма документа транзакции в JVM-сборке.
 *
 * Смотрит на сырой документ, а не на возвращённое значение: обе сборки ходят в одну и ту же базу,
 * и отличить `ObjectId` от строки, а decimal128 от строки по результату `find` невозможно — своя
 * запись читается своим же кодеком. Расхождение вылезло бы уже на чужих данных: native-сборка не
 * нашла бы документ по идентификатору и посчитала бы сумму нечитаемой.
 *
 * Парный набор для нативной сборки — `MongknStorageTest`.
 */
class MongoTransactionDocumentTest {

    @Test
    fun `transaction lands in mongo with an ObjectId and a decimal amount`() = maniTest { mongoUri ->
        val token = client.signIn("shape", "hunter22")
        val response = client.postTransaction(token, aTransaction("обед").copy(amount = "1234.56".toBigDecimal()))
        val created = maniJson.decodeFromString(Transaction.serializer(), response.bodyAsText())

        MongoClient.create(mongoUri).use { mongo ->
            val raw = mongo.getDatabase("mani-test")
                .getCollection<Document>(TRANSACTION_COLLECTION)
                .find(Filters.eq("_id", ObjectId(created.id)))
                .firstOrNull()

            assertNotNull(raw, "документ не найден по ObjectId — идентификатор лёг не тем типом")
            assertTrue(raw["_id"] is ObjectId, "_id должен быть ObjectId, а не ${raw["_id"]}")
            assertTrue(raw["amount"] is Decimal128, "amount должен быть decimal128, а не ${raw["amount"]}")
            assertEquals("1234.56", (raw["amount"] as Decimal128).bigDecimalValue().toPlainString())
        }

        // Положительный контроль: смотрели на ту самую запись, а не на пустую коллекцию.
        assertEquals("1234.56", client.transactions(token).single().amount.toPlainString())
    }
}
