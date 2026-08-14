package ru.workinprogress.feature.transaction.data

import kotlinx.datetime.LocalDate
import ru.workinprogress.feature.transaction.Category
import ru.workinprogress.feature.transaction.Transaction
import ru.workinprogress.utilz.bigdecimal.BigDecimalSerializable

/**
 * Транзакция, какой она лежит в хранилище: с `categoryId`, а не с самой категорией.
 *
 * Категории хранятся в документе пользователя, а не рядом с транзакцией, поэтому собрать
 * [Transaction] можно только зная список категорий владельца. Раньше этим занимался маршрут —
 * и продолжает: репозиторий отдаёт запись, маршрут подставляет категорию.
 *
 * Тип нужен ещё и затем, чтобы `userId` не пришлось тащить в клиентский [Transaction]: владелец
 * записи — понятие сервера, клиенту он не нужен и в контракт не входит.
 */
data class TransactionRecord(
    val id: String,
    val amount: BigDecimalSerializable,
    val income: Boolean,
    val date: LocalDate,
    val until: LocalDate?,
    val period: Transaction.Period,
    val comment: String,
    val userId: String,
    val categoryId: String?,
)

fun TransactionRecord.toTransaction(userCategories: List<Category>): Transaction =
    Transaction(
        id = id,
        amount = amount,
        income = income,
        date = date,
        period = period,
        until = until,
        comment = comment,
        category = userCategories.find { it.id == categoryId } ?: Category.default,
    )

interface TransactionRepository {
    suspend fun create(
        transaction: Transaction,
        userId: String,
    ): String

    suspend fun getByUser(userId: String): List<TransactionRecord>

    suspend fun getById(id: String): TransactionRecord?

    suspend fun update(
        transaction: Transaction,
        userId: String,
    )

    suspend fun delete(id: String): Boolean

    /** Все транзакции владельца разом. Нужно уборке песочниц. */
    suspend fun deleteByUser(userId: String)
}
