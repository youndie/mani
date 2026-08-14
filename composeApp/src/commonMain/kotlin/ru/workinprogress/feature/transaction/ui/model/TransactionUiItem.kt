package ru.workinprogress.feature.transaction.ui.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.datetime.LocalDate
import ru.workinprogress.feature.currency.Currency
import ru.workinprogress.feature.transaction.Category
import ru.workinprogress.feature.transaction.Transaction

data class TransactionUiItem(
    val id: String,
    val amount: BigDecimal,
    val income: Boolean,
    val date: LocalDate,
    val until: LocalDate?,
    val period: Transaction.Period,
    val comment: String,
    val currency: Currency,
    val category: Category,
) {
    /**
     * Сумма для ленты: цветом выделяется только доход.
     *
     * В макете расход набран обычным цветом текста, и не случайно — трат в списке большинство,
     * и красными они превращают ленту в сплошное предупреждение. Красный оставлен там, где он
     * что-то значит: в форме и в предупреждении о дне обнуления.
     */
    val amountText get() = buildColoredAmount(amount, currency, income, negativeColor = Color.Unspecified)

    companion object {
        operator fun invoke(
            transaction: Transaction,
            currency: Currency,
        ): TransactionUiItem =
            TransactionUiItem(
                id = transaction.id,
                amount = transaction.amount,
                income = transaction.income,
                date = transaction.date,
                until = transaction.until,
                period = transaction.period,
                comment = transaction.comment,
                currency = currency,
                category = transaction.category,
            )
    }
}

/**
 * Знак суммы — акцентами палитры, а не чистыми «светофорными» цветами.
 *
 * Чистый красный на тёмном фоне кричит громче, чем стоит трата, и выбивается из всей остальной
 * палитры; зелёный из той же пары к ней вообще не относится. Оттенки взяты из макета.
 */
val PositiveColor = Color(0xFFD6C68D)
val NegativeColor = Color(0xFFFFB4AB)

fun buildColoredAmount(
    amount: String,
    amountValue: BigDecimal = (
        try {
            amount.toBigDecimal()
        } catch (e: Exception) {
            BigDecimal.ZERO
        }
    ),
    currency: Currency,
    sign: Boolean = amountValue > 0,
) = buildColoredAmount(amountValue, currency, sign)

fun buildColoredAmount(
    amount: BigDecimal,
    currency: Currency,
    sign: Boolean = amount > 0,
    useSign: Boolean = true,
    negativeColor: Color = NegativeColor,
): AnnotatedString =
    buildAnnotatedString {
        if (amount != BigDecimal.ZERO) {
            withStyle(style = SpanStyle(color = if (sign) PositiveColor else negativeColor)) {
                if (useSign) {
                    append(if (sign) "+" else "−")
                }
                append(formatMoneyAbsolute(amount, currency))
            }
        } else {
            append(formatMoneyAbsolute(amount, currency))
        }
    }

fun formatMoneyAbsolute(
    amount: BigDecimal,
    currency: Currency,
) = formatMoney(amount.abs(), currency)

fun formatMoney(
    amount: BigDecimal,
    currency: Currency,
) = "${groupThousands(amount.toPlainString())} ${currency.symbol}"

/** Неразрывный: «4 895» не должно разъезжаться по двум строкам. */
private const val GROUP_SEPARATOR = '\u00A0'
private const val GROUP_SIZE = 3

/**
 * Разряды через пробел: «4895 $» читается хуже, чем «4 895 $», а на шести знаках — заметно хуже.
 *
 * Группируется только целая часть; знак и дробная остаются как есть. `java.text` в общем коде
 * недоступен, поэтому вручную.
 */
internal fun groupThousands(plain: String): String {
    val negative = plain.startsWith('-')
    val body = plain.removePrefix("-")
    val integer = body.substringBefore('.')
    val fraction = body.substringAfter('.', "")

    val grouped = integer
        .reversed()
        .chunked(GROUP_SIZE)
        .joinToString(GROUP_SEPARATOR.toString())
        .reversed()

    return buildString {
        if (negative) append('-')
        append(grouped)
        if (fraction.isNotEmpty()) {
            append('.')
            append(fraction)
        }
    }
}

fun formatMoneyAbsolute(
    amount: String,
    currency: Currency,
) = formatMoneyAbsolute(amount.toBigDecimal(), currency)
