package ru.workinprogress.feature.main

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import ru.workinprogress.feature.auth.domain.LogoutUseCase
import ru.workinprogress.feature.categories.domain.GetCategoriesUseCase
import ru.workinprogress.feature.currency.Currency
import ru.workinprogress.feature.currency.GetCurrentCurrencyUseCase
import ru.workinprogress.feature.demo.domain.SeedUseCase
import ru.workinprogress.feature.main.ui.FiltersState
import ru.workinprogress.feature.main.ui.ForecastUiState
import ru.workinprogress.feature.main.ui.MainUiState
import ru.workinprogress.feature.transaction.*
import ru.workinprogress.feature.transaction.domain.DeleteTransactionsUseCase
import ru.workinprogress.feature.transaction.domain.GetTransactionsUseCase
import ru.workinprogress.feature.transaction.ui.model.NegativeColor
import ru.workinprogress.feature.transaction.ui.model.PositiveColor
import ru.workinprogress.feature.transaction.ui.model.TransactionUiItem
import ru.workinprogress.feature.transaction.ui.model.formatMoney
import ru.workinprogress.mani.emptyImmutableMap
import ru.workinprogress.mani.today
import ru.workinprogress.useCase.UseCase
import ru.workinprogress.utilz.bigdecimal.sumOf


class MainViewModel(
	private val transactionsUseCase: GetTransactionsUseCase,
	private val deleteTransactionsUseCase: DeleteTransactionsUseCase,
	private val getCurrencyUseCase: GetCurrentCurrencyUseCase,
	private val getCategoriesUseCase: GetCategoriesUseCase,
	private val logoutUseCase: LogoutUseCase,
	private val seedUseCase: SeedUseCase,
	private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

	private val state = MutableStateFlow(MainUiState(loading = true, transactions = loadingItems))

	private val filterUpcoming = MutableStateFlow(true)
	private val filterCategory = MutableStateFlow<Category?>(null)

	val observe = state.asStateFlow()

	private lateinit var currency: Currency

	init {
		viewModelScope.launch {
			currency = getCurrencyUseCase.get()
			load()
		}
	}

	private suspend fun load() {
		state.value = MainUiState(loading = true, transactions = loadingItems)

		val result = withContext(dispatcher) { transactionsUseCase() }

		when (result) {
			is UseCase.Result.Error -> {
				state.value = MainUiState(errorMessage = result.throwable.message)
			}

			is UseCase.Result.Success -> {
				state.value = state.value.copy(loading = true, transactions = emptyImmutableMap())

				combine(
					result.data,
					getCategoriesUseCase.get(),
					filterUpcoming,
					filterCategory
				) { transactions, categories, upcoming, category ->
					val simulationResult = transactions.simulate()

					MainUiState(
						loading = false,
						filtersState = FiltersState(
							categories = (categories + Category.default).toImmutableSet(),
							upcoming = upcoming,
							category = category,
							loading = false,
						),
						transactions = simulationResult
							.filterKeys {
								if (upcoming) {
									today() <= it
								} else {
									today() > it
								}
							}
							.mapValues {
								it.value.filter {
									category == null || category == it.category
								}
							}
							.filterValues { transactions -> transactions.isNotEmpty() }
							.mapValues { entry ->
								entry.value.map { transaction ->
									TransactionUiItem(transaction, currency)
								}.toImmutableList()
							}
							.entries
							.run {
								if (upcoming) {
									sortedBy { it.key }
								} else {
									sortedByDescending { it.key }
								}
							}
							.associate { it.key to it.value }.toImmutableMap(),
						forecast = buildForecast(simulationResult, currency),
						dayBalances = buildDayBalances(simulationResult, currency)
					)
				}.flowOn(dispatcher).collectLatest { result: MainUiState ->
					state.update { result }
				}
			}
		}
	}

	fun onTransactionSelected(transactionUiItem: TransactionUiItem) {
		if (transactionUiItem in state.value.selectedTransactions) {
			state.update { state ->
				state.copy(
					selectedTransactions = (state.selectedTransactions - transactionUiItem).toImmutableList()
				)
			}
		} else {
			state.update { state ->
				state.copy(
					selectedTransactions = (state.selectedTransactions + transactionUiItem).toImmutableList()
				)
			}
		}
	}

	fun onDeleteClicked() {
		viewModelScope.launch {
			val selected = state.value.selectedTransactions.map { it.id }
			state.update { state ->
				state.copy(
					showDeleteDialog = false,
					selectedTransactions = emptyList<TransactionUiItem>().toImmutableList()
				)
			}

			withContext(dispatcher) {
				deleteTransactionsUseCase(selected)
			}
		}
	}

	fun onContextMenuClosed() {
		state.update { state ->
			state.copy(
				selectedTransactions = emptyList<TransactionUiItem>().toImmutableList()
			)
		}
	}

	fun onShowDeleteDialogClicked() {
		state.value = state.value.copy(showDeleteDialog = true)
	}

	fun onDismissDeleteDialog() {
		state.update {
			it.copy(showDeleteDialog = false)
		}
	}


	/** Заполнить пустой аккаунт данными сида — предложение с первого экрана. */
	fun onFillWithDemoDataClicked() {
		viewModelScope.launch {
			val result = seedUseCase()
			if (result is UseCase.Result.Error) {
				state.update { it.copy(errorMessage = result.throwable.message) }
			}
		}
	}

	fun onProfileClicked() {
		state.update { state ->
			state.copy(showProfile = true)
		}
	}

	fun onProfileDismiss() {
		state.update { state ->
			state.copy(showProfile = false)
		}
	}

	fun onLogoutClicked() {
		viewModelScope.launch {
			logoutUseCase()
			state.value = MainUiState()
		}
	}

	fun onUpcomingToggle(bool: Boolean) {
		filterUpcoming.value = bool
	}

	fun onCategorySelected(category: Category?) {
		filterCategory.value = category
	}

	companion object {
		val loadingItems by lazy {
			mapOf(
				today() to (0..2).map {
					TransactionUiItem(
						it.toString(),
						BigDecimal.ZERO,
						false,
						date = today(),
						until = null,
						period = Transaction.Period.OneTime,
						comment = "Loading",
						currency = Currency.Usd,
						category = Category.default,
					)
				}.toImmutableList()
			).toImmutableMap()
		}


		private fun Map<LocalDate, List<Transaction>>.sumByMonth(monthDate: LocalDate) =
			this
				.filter {
					it.key.monthNumber == monthDate.monthNumber &&
							it.key.year == monthDate.year
				}.flatMap { it.value }.sumOf { it.amountSigned }

		/**
		 * Собирает героя экрана: дату, когда деньги кончатся, и баланс на сегодня.
		 *
		 * Дни считаются от **ключа** карты, а не от `transaction.date`: симуляция раскладывает по
		 * дням один и тот же объект, и его `date` — это день заведения правила.
		 */
		internal fun buildForecast(
			simulationResult: Map<LocalDate, List<Transaction>>,
			currency: Currency,
			today: LocalDate = today(),
		): ForecastUiState {
			if (simulationResult.values.all { it.isEmpty() }) return ForecastUiState.Empty

			val balanceToday = simulationResult.entries
				.runningFold(BigDecimal.ZERO) { acc, entry ->
					if (entry.key > today) acc
					else acc + entry.value.sumOf { it.amountSigned }
				}.last()

			val balanceText = formatMoney(balanceToday, currency)
			val negativeDate = simulationResult.findZeroEvents().second

			// Дата в прошлом — не прогноз: баланс уже в минусе, и обещать «деньги кончатся» про
			// вчера бессмысленно.
			val runsOut = negativeDate?.takeIf { it > today }
				?: return ForecastUiState.Steady(balanceText)

			return ForecastUiState.RunsOut(
				runsOutOn = runsOut.format(dayMonthFormat),
				daysLeft = today.daysUntil(runsOut),
				balanceToday = balanceText,
			)
		}

		/**
		 * Баланс на конец каждого дня.
		 *
		 * Считается по **всей** симуляции, а не по видимой ленте: фильтр по категории — это способ
		 * посмотреть, а не другая реальность, и баланс от него меняться не должен. Иначе лента и
		 * линия графика показывали бы разные числа для одного дня.
		 */
		internal fun buildDayBalances(
			simulationResult: Map<LocalDate, List<Transaction>>,
			currency: Currency,
		): ImmutableMap<LocalDate, String> {
			var running = BigDecimal.ZERO

			return simulationResult
				.mapValues { (_, transactions) ->
					running += transactions.sumOf { it.amountSigned }
					formatMoney(running, currency)
				}.toImmutableMap()
		}

		private val dayMonthFormat = LocalDate.Format {
			dayOfMonth()
			char(' ')
			monthName(MonthNames.ENGLISH_FULL)
		}
	}
}

