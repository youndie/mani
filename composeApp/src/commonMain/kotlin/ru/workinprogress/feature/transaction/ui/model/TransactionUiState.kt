package ru.workinprogress.feature.transaction.ui.model

import androidx.compose.ui.text.AnnotatedString
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import ir.ehsannarmani.compose_charts.extensions.format
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import ru.workinprogress.feature.currency.Currency
import ru.workinprogress.feature.transaction.Category
import ru.workinprogress.feature.transaction.Transaction
import ru.workinprogress.mani.today

data class TransactionUiState(
	val id: String = "temp",
	val amount: String = "",
	/** Расход по умолчанию: их вносят чаще, чем доходы, и правило чаще заводят на трату. */
	val income: Boolean = false,
	val period: Transaction.Period = Transaction.Period.OneTime,
	val comment: String = "",
	val date: DateDataUiState = DateDataUiState(),
	val until: DateDataUiState = DateDataUiState(),
	val category: Category = Category.default,

	val periods: ImmutableList<Transaction.Period> = defaultPeriods,
	val categories: ImmutableSet<Category> = defaultCategories,

	val success: Boolean = false,
	val loading: Boolean = false,
	val edit: Boolean = false,

	val errorMessage: String? = null,

	val futureInformation: AnnotatedString = AnnotatedString(""),

	/** Насколько это правило сдвигает день, когда деньги кончатся. `null` — если не сдвигает. */
	val runsOutShift: RunsOutShift? = null,

	val currency: Currency = Currency("", "", ""),
) {
	val periodsExpanded get() = periods != defaultPeriods
	val categoriesExpanded get() = true

	/**
	 * Дата обязательна: правило без неё не разворачивается в календарь, а кнопка «Create»
	 * при одной введённой сумме приглашала сохранить то, что сохранить нельзя.
	 */
	val valid get() = amountError == null && amount.isNotEmpty() && date.value != null

	/**
	 * Почему сумма не годится — под самим полем, а не в общем сообщении внизу.
	 *
	 * Ноль тут не придирка: правило на ноль ничего не сдвигает в прогнозе, то есть не делает
	 * того единственного, ради чего заводится. А неактивная кнопка без объяснения оставляла
	 * человека гадать, чего от него ждут.
	 */
	val amountError: String?
		get() = when {
			amount.isEmpty() -> null
			amount.toDoubleOrNull() == null -> "this is not an amount"
			amount.toDouble() == 0.0 -> "an amount is required"
			else -> null
		}
	val tempTransaction get() = buildTransaction(this)

	private fun buildTransaction(stateValue: TransactionUiState): Transaction {
		return Transaction(
			id = stateValue.id,
			amount = try {
				stateValue.amount.toBigDecimal()
			} catch (e: Exception) {
				BigDecimal.ZERO
			},
			income = stateValue.income,
			period = stateValue.period,
			date = stateValue.date.value ?: today(),
			until = stateValue.until.value,
			comment = stateValue.comment,
			category = stateValue.category
		)
	}

	companion object {
		operator fun invoke(transaction: Transaction?, currency: Currency) = transaction?.let {
			TransactionUiState(
				transaction.id,
				transaction.amount.toPlainString(),
				transaction.income,
				transaction.period,
				category = transaction.category,
				periods = defaultPeriods,
				comment = transaction.comment,
				date = DateDataUiState(transaction.date),
				until = DateDataUiState(transaction.until),
				currency = currency
			)
		} ?: TransactionUiState()

		/** Четвёрка из макета: разовая трата, две недели, месяц и год. Остальное — под «More». */
		private val defaultPeriods = listOf(
			Transaction.Period.OneTime,
			Transaction.Period.TwoWeek,
			Transaction.Period.Month,
			Transaction.Period.Year,
		).toImmutableList()

		private val defaultCategories = persistentSetOf<Category>(Category.default)
	}
}

/**
 * Сдвиг дня обнуления от этого правила.
 *
 * [worse] отделяет «деньги кончатся раньше» от «позже»: это две разные новости, и красным
 * помечать надо только первую.
 */
data class RunsOutShift(val text: String, val worse: Boolean)

@Serializable
data class DateDataUiState(val value: LocalDate? = null, val showDatePicker: Boolean = false)