package ru.workinprogress.feature.transaction

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import ru.workinprogress.feature.chart.ChartResponse
import ru.workinprogress.mani.today
import ru.workinprogress.utilz.bigdecimal.sumOf
import kotlin.math.sign

const val DEFAULT_PERIOD_VALUE = 3
val DEFAULT_PERIOD_UNIT = DateTimeUnit.MONTH

const val LARGE_PERIOD_VALUE = 1
val LARGE_PERIOD_UNIT = DateTimeUnit.YEAR


fun createDates(from: LocalDate, to: LocalDate): List<LocalDate> {
	return buildList {
		var currentDate = from
		while (currentDate < to) {
			add(currentDate)
			currentDate = currentDate.plus(1, DateTimeUnit.DAY)
		}
	}
}

fun List<Transaction>.defaultPeriod(): Pair<LocalDate, LocalDate> {
	val from = this.minOfOrNull { transaction -> transaction.date } ?: today()
	val to = defaultPeriodAppend(today())
	return from to to
}

fun defaultPeriodAppend(date: LocalDate) = date.plus(DEFAULT_PERIOD_VALUE, DEFAULT_PERIOD_UNIT)
fun largePeriodAppend(date: LocalDate) = date.plus(LARGE_PERIOD_VALUE, LARGE_PERIOD_UNIT)

fun List<Transaction>.toChartInternal(): ChartResponse {
	if (isEmpty()) return ChartResponse.Empty

	val (from, to) = defaultPeriod()
	val simulated = simulate(from, to)
	val chartData = simulated.entries.runningFold(from to 0.toBigDecimal()) { acc, list ->
		list.key to acc.second + list.value.sumOf { transaction ->
			transaction.amountSigned
		}
	}.toMap()

	return ChartResponse(days = chartData, from, to)
}

fun List<Transaction>.simulate(
	dates: Pair<LocalDate, LocalDate> = defaultPeriod(),
): Map<LocalDate, List<Transaction>> {
	val (from, to) = dates
	return simulate(from, to)
}

fun List<Transaction>.simulate(from: LocalDate, to: LocalDate): Map<LocalDate, List<Transaction>> {
	val scheduled = mutableMapOf<LocalDate, List<Transaction>>()

	createDates(from, to).forEach { currentDate ->
		val scheduledForDate = mutableListOf<Transaction>()

		val currentTransactions = this.filter { transaction ->
			transaction.date == currentDate
		}

		val nextTransactions = scheduled[currentDate].orEmpty()

		(currentTransactions + nextTransactions).forEach { transaction ->
			scheduledForDate.add(transaction)

			scheduled.scheduleTransaction(
				transaction, to,
				when (transaction.period) {
					Transaction.Period.Day -> currentDate.plus(1, DateTimeUnit.DAY)
					Transaction.Period.OneTime -> currentDate
					Transaction.Period.Week -> currentDate.plus(1, DateTimeUnit.WEEK)
					Transaction.Period.TwoWeek -> currentDate.plus(2, DateTimeUnit.WEEK)
					Transaction.Period.Month -> currentDate.plus(1, DateTimeUnit.MONTH)
					Transaction.Period.ThreeMonth -> currentDate.plus(1, DateTimeUnit.QUARTER)
					Transaction.Period.HalfYear -> currentDate.plus(2, DateTimeUnit.QUARTER)
					Transaction.Period.Year -> currentDate.plus(1, DateTimeUnit.YEAR)
				}
			)
		}

		scheduled[currentDate] = scheduledForDate
	}

	return scheduled.toList().sortedBy { it.first }.toMap()
}

/**
 * Ищет дни, в которые баланс меняет знак.
 *
 * Дата берётся из **ключа**, а не из `transaction.date`: [simulate] раскладывает по дням одну и ту
 * же транзакцию, и её поле `date` — дата начала правила, а не дня, на который пришлось вхождение.
 * Пока сбой ловился только правилами, начатыми в прошлом: сумма пересекала ноль в будущем, а
 * наружу уходил день заведения правила — то есть дата, которая уже прошла.
 */
fun Map<LocalDate, List<Transaction>>.findZeroEvents(): Pair<LocalDate?, LocalDate?> {
	var positiveDate: LocalDate? = null
	var negativeDate: LocalDate? = null

	entries.runningFoldIndexed(
		BigDecimal.ZERO,
		{ index, acc, item ->
			val nextValue = acc + item.value.sumOf { transaction ->
				transaction.amountSigned
			}

			if (index == 0) return@runningFoldIndexed nextValue

			val alreadyChangeSign = positiveDate != null || negativeDate != null
			if (nextValue.signum() != acc.signum() && (acc != BigDecimal.ZERO || alreadyChangeSign)) {
				if (nextValue.signum() > acc.signum()) {
					positiveDate = item.key
				} else {
					negativeDate = item.key
				}
			}

			if (positiveDate != null && negativeDate != null) {
				return positiveDate to negativeDate
			}

			nextValue
		})

	return positiveDate to negativeDate
}

private fun MutableMap<LocalDate, List<Transaction>>.scheduleTransaction(
	transaction: Transaction,
	to: LocalDate,
	nextDate: LocalDate,
) {
	if ((transaction.until == null || transaction.until >= nextDate) && to > nextDate) {
		this[nextDate] = (this[nextDate]?.toMutableList() ?: mutableListOf()).apply {
			this.add(transaction)
		}
	}
}
