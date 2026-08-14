package ru.workinprogress.feature.transaction.data

import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId
import java.math.BigDecimal

data class TransactionDb(
    @BsonId val id: ObjectId,
    val amount: BigDecimal,
    val income: Boolean,
    val date: String,
    val until: String?,
    val period: String,
    val comment: String,
    val userId: String,
    val categoryId: String?,
)
