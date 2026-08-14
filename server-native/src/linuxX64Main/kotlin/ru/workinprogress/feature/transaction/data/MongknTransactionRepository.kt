package ru.workinprogress.feature.transaction.data

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.LocalDate
import ru.workinprogress.feature.transaction.Transaction
import ru.workinprogress.mani.db.TRANSACTION_COLLECTION
import ru.workinprogress.mani.db.TransactionDb
import ru.workinprogress.mongkn.MongoDatabase
import ru.workinprogress.mongkn.bson.BsonObjectId
import ru.workinprogress.mongkn.ext.filter
import ru.workinprogress.mongkn.ext.find

class MongknTransactionRepository(mongoDatabase: MongoDatabase) : TransactionRepository {
    private val db = mongoDatabase.getCollection<TransactionDb>(TRANSACTION_COLLECTION)

    override suspend fun create(transaction: Transaction, userId: String): String {
        val id = BsonObjectId.generate().hex
        db.insertOne(mapToDb(transaction, id, userId))
        return id
    }

    override suspend fun getById(id: String): TransactionRecord? = db.find { "_id" eq id }.firstOrNull()?.toRecord()

    override suspend fun getByUser(userId: String): List<TransactionRecord> = db
        .find { TransactionDb::userId eq userId }
        .toList()
        .map { it.toRecord() }

    override suspend fun update(transaction: Transaction, userId: String) {
        db.replaceOne(
            filter = filter<TransactionDb> { "_id" eq transaction.id },
            replacement = mapToDb(transaction, transaction.id, userId),
        )
    }

    override suspend fun delete(id: String): Boolean =
        db.deleteOne(filter<TransactionDb> { "_id" eq id }).deletedCount > 0

    override suspend fun deleteByUser(userId: String) {
        db.deleteMany(filter<TransactionDb> { TransactionDb::userId eq userId })
    }

    private fun mapToDb(transaction: Transaction, id: String, userId: String) = TransactionDb(
        id = id,
        amount = transaction.amount,
        income = transaction.income,
        date = transaction.date.toString(),
        until = transaction.until?.toString(),
        period = transaction.period.name,
        comment = transaction.comment,
        userId = userId,
        categoryId = transaction.category.id,
    )

    private fun TransactionDb.toRecord() = TransactionRecord(
        id = id,
        amount = amount,
        income = income,
        date = LocalDate.parse(date),
        until = until?.let(LocalDate::parse),
        period = Transaction.Period.valueOf(period),
        comment = comment,
        userId = userId,
        categoryId = categoryId,
    )
}
