package ru.workinprogress.mani.db

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import ru.workinprogress.mongkn.bson.BsonDecimal128
import ru.workinprogress.mongkn.bson.BsonDecoder
import ru.workinprogress.mongkn.bson.BsonEncoder
import ru.workinprogress.mongkn.ext.StringAsBsonObjectId

/** Имена коллекций — те же, что у JVM-сборки: обе сборки ходят в одну базу. */
const val USER_COLLECTION = "users"
const val TRANSACTION_COLLECTION = "transaction"

/**
 * Документы такие, какими их пишет JVM-сборка.
 *
 * Совпадать обязано **представление на проводе**, а не форма классов: обе сборки ходят в одну
 * базу стенда, и расхождение здесь не падает, а молча ничего не находит. Два места, где это
 * легко потерять:
 *
 * * `_id` — `ObjectId`, а не строка. `@SerialName("_id")` даёт имя, `StringAsBsonObjectId` —
 *   тип. Без первого MongoDB заведёт свой `_id`, а поле уедет обычным; без второго фильтр по
 *   `_id` уйдёт строкой и не совпадёт ни с одним документом;
 * * `amount` — `decimal128`. Общий `BigDecimalSerializer` из `:shared` пишет строку, потому что
 *   создавался для JSON; в BSON у денег есть точный тип, и JVM-драйвер писал именно его.
 */
@Serializable
data class UserDb(
    @SerialName("_id")
    @Serializable(with = StringAsBsonObjectId::class)
    val id: String,
    val username: String,
    val password: String,
    val salt: String? = null,
    val tokens: List<String> = emptyList(),
    val categories: List<CategoryDb> = emptyList(),
)

@Serializable
data class CategoryDb(
    @SerialName("_id")
    @Serializable(with = StringAsBsonObjectId::class)
    val id: String,
    val name: String,
)

@Serializable
data class TransactionDb(
    @SerialName("_id")
    @Serializable(with = StringAsBsonObjectId::class)
    val id: String,
    @Serializable(with = BigDecimalAsBsonDecimal128::class)
    val amount: BigDecimal,
    val income: Boolean,
    val date: String,
    val until: String? = null,
    val period: String,
    val comment: String,
    val userId: String,
    val categoryId: String? = null,
)

/**
 * Деньги как BSON `decimal128` — тот же тип, что пишет `java.math.BigDecimal` через официальный
 * драйвер.
 *
 * Строка сюда не годится: по ней не считает `$sum`, её иначе сортирует сервер, и главное — уже
 * записанные документы читаются обратно только этим типом.
 */
object BigDecimalAsBsonDecimal128 : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ru.workinprogress.mani.db.BigDecimalAsBsonDecimal128", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        val bson = encoder as? BsonEncoder ?: throw SerializationException("amount сериализуется только в BSON")
        bson.encodeBsonValue(BsonDecimal128(value.toPlainString()))
    }

    override fun deserialize(decoder: Decoder): BigDecimal {
        val bson = decoder as? BsonDecoder ?: throw SerializationException("amount читается только из BSON")
        val value = bson.decodeBsonValue()
        val text =
            (value as? BsonDecimal128)?.value
                ?: throw SerializationException("ожидался decimal128, пришло $value")
        return text.toBigDecimal()
    }
}
