package ru.workinprogress.feature.transaction

import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import ru.workinprogress.utilz.bigdecimal.BigDecimalSerializable

@Serializable
data class Transaction(
    override val id: String,
    val amount: BigDecimalSerializable,
    val income: Boolean,
    val date: LocalDate,
    val until: LocalDate?,
    val period: Period,
    val comment: String,
    val category: Category = Category.default,
) : WithId {

    enum class Period {
        OneTime,
        Day,
        Week,
        TwoWeek,
        Month,
        ThreeMonth,
        HalfYear,
        Year,
    }
}

@Serializable
data class Category(override val id: String, val name: String) : WithId {
    companion object {
        val default = Category("0", "Default")
    }
}

val Transaction.amountSigned get() = this.amount * (if (this.income) 1 else -1).toBigDecimal()
