package ru.workinprogress.feature.transaction.data

import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import com.ionspin.kotlin.bignum.decimal.toJavaBigDecimal
import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.LocalDate
import org.bson.types.ObjectId
import ru.workinprogress.feature.transaction.Transaction
import ru.workinprogress.mani.db.deleteById

const val TRANSACTION_COLLECTION = "transaction"

class MongoTransactionRepository(
    mongoDatabase: MongoDatabase,
) : TransactionRepository {
    private val db = mongoDatabase.getCollection<TransactionDb>(TRANSACTION_COLLECTION)

    override suspend fun create(
        transaction: Transaction,
        userId: String,
    ): String {
        val new = mapToDb(transaction, userId = userId)
        db.insertOne(new)
        return new.id.toHexString()
    }

    override suspend fun getById(id: String): TransactionRecord? =
        db.find(Filters.eq("_id", ObjectId(id))).firstOrNull()?.toRecord()

    override suspend fun getByUser(userId: String): List<TransactionRecord> =
        db
            .find<TransactionDb>(Filters.eq(TransactionDb::userId.name, userId))
            .toList()
            .map { it.toRecord() }

    override suspend fun update(
        transaction: Transaction,
        userId: String,
    ) {
        val id = ObjectId(transaction.id)
        db.replaceOne(Filters.eq("_id", id), mapToDb(transaction, id, userId))
    }

    override suspend fun delete(id: String): Boolean = db.deleteById(id)

    override suspend fun deleteByUser(userId: String) {
        db.deleteMany(Filters.eq(TransactionDb::userId.name, userId))
    }

    private fun mapToDb(
        transaction: Transaction,
        id: ObjectId = ObjectId(),
        userId: String,
    ) = TransactionDb(
        id = id,
        amount = transaction.amount.toJavaBigDecimal(),
        income = transaction.income,
        date = transaction.date.toString(),
        until = transaction.until?.toString(),
        period = transaction.period.name,
        comment = transaction.comment,
        userId = userId,
        categoryId = transaction.category.id,
    )

    private fun TransactionDb.toRecord() =
        TransactionRecord(
            id = id.toHexString(),
            amount = amount.toPlainString().toBigDecimal(),
            income = income,
            date = LocalDate.parse(date),
            until = until?.let(LocalDate::parse),
            period = Transaction.Period.valueOf(period),
            comment = comment,
            userId = userId,
            categoryId = categoryId,
        )
}
