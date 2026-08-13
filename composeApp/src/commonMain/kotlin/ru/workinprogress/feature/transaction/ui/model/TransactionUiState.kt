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

	val currency: Currency = Currency("", "", ""),
) {
	val periodsExpanded get() = periods != defaultPeriods
	val categoriesExpanded get() = true

	val valid get() = amount.toDoubleOrNull() != null
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

		private val defaultPeriods = listOf(
			Transaction.Period.OneTime, Transaction.Period.TwoWeek, Transaction.Period.Month
		).toImmutableList()

		private val defaultCategories = persistentSetOf<Category>(Category.default)
	}
}

@Serializable
data class DateDataUiState(val value: LocalDate? = null, val showDatePicker: Boolean = false)