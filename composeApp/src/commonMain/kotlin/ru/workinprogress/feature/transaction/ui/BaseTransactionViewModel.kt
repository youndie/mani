package ru.workinprogress.feature.transaction.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import ru.workinprogress.feature.categories.domain.AddCategoryUseCase
import ru.workinprogress.feature.categories.domain.DeleteCategoryUseCase
import ru.workinprogress.feature.categories.domain.ObserveCategoriesUseCase
import ru.workinprogress.feature.transaction.*
import ru.workinprogress.feature.transaction.ui.component.formatted
import ru.workinprogress.feature.transaction.ui.component.model.TransactionAction
import ru.workinprogress.feature.transaction.domain.ObserveTransactionsUseCase
import ru.workinprogress.feature.transaction.ui.model.RunsOutShift
import ru.workinprogress.feature.transaction.ui.model.TransactionUiState
import ru.workinprogress.feature.transaction.ui.model.buildColoredAmount
import ru.workinprogress.mani.orToday
import ru.workinprogress.mani.today
import ru.workinprogress.useCase.UseCase
import ru.workinprogress.utilz.bigdecimal.sumOf

abstract class BaseTransactionViewModel(
    private val addCategoryUseCase: AddCategoryUseCase,
    private val observeCategoriesUseCase: ObserveCategoriesUseCase,
    private val deleteCategoryUseCase: DeleteCategoryUseCase,
    private val observeTransactionsUseCase: ObserveTransactionsUseCase,
    protected val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    protected open val state = MutableStateFlow(TransactionUiState())
    val observe get() = state.asStateFlow()

    /** Остальные правила — фон, относительно которого считается сдвиг дня обнуления. */
    private var others: List<Transaction> = emptyList()

    abstract fun onSubmitClicked()

    protected fun observeTransactions() {
        viewModelScope.launch {
            observeTransactionsUseCase.observe.collectLatest { transactions ->
                others = transactions
                state.update { it.addFutureInformation() }
            }
        }
    }

    protected fun observeCategories() {
        viewModelScope.launch {
            observeCategoriesUseCase.observe.collectLatest { value ->
                state.update { state ->
                    state.copy(categories = (value + Category.default).toImmutableSet())
                }
            }
        }
    }

    fun onAction(action: TransactionAction) {
        when (action) {
            is TransactionAction.AmountChanged -> onAmountChanged(action.amount)
            is TransactionAction.CategoryChanged -> onCategoryChanged(action.category)
            is TransactionAction.CategoryCreate -> onCategoryCreate(action.name)
            is TransactionAction.CategoryDelete -> onCategoryDelete(action.category)
            is TransactionAction.CommentChanged -> onCommentChanged(action.comment)
            is TransactionAction.DateSelected -> onDateSelected(action.date)
            is TransactionAction.DateUntilSelected -> onDateUntilSelected(action.date)
            TransactionAction.ExpandCategoryClicked -> onExpandCategoryClicked()
            TransactionAction.ExpandPeriodClicked -> onExpandPeriodClicked()
            is TransactionAction.IncomeChanged -> onIncomeChanged(action.income)
            is TransactionAction.PeriodChanged -> onPeriodChanged(action.period)
            TransactionAction.SubmitClicked -> onSubmitClicked()
            TransactionAction.ToggleDatePicker -> onToggleDatePicker()
            TransactionAction.ToggleUntilDatePicker -> onToggleUntilDatePicker()
        }
    }

    internal fun onAmountChanged(amount: String) {
        if (amount.toDoubleOrNull() != null || amount.isEmpty()) {
            state.update { state ->
                state.copy(amount = amount).addFutureInformation()
            }
        }
    }

    internal fun onCommentChanged(comment: String) = state.update { state ->
        state.copy(comment = comment)
    }

    internal fun onIncomeChanged(income: Boolean) = state.update { state ->
        state.copy(income = income).addFutureInformation()
    }

    internal fun onPeriodChanged(period: Transaction.Period) = state.update { state ->
        state.copy(period = period).addFutureInformation()
    }

    internal fun onExpandPeriodClicked() = state.update { state ->
        state.copy(periods = Transaction.Period.entries.toImmutableList())
    }

    internal fun onExpandCategoryClicked() {}

    internal fun onToggleDatePicker() = state.update { state ->
        state.copy(date = state.date.copy(showDatePicker = state.date.showDatePicker.not()))
    }

    internal fun onToggleUntilDatePicker() = state.update { state ->
        state.copy(until = state.until.copy(showDatePicker = state.until.showDatePicker.not()))
    }

    internal fun onDateSelected(date: LocalDate) = state.update { state ->
        state.copy(date = state.date.copy(value = date, showDatePicker = false)).addFutureInformation()
    }

    internal fun onDateUntilSelected(date: LocalDate) = state.update { state ->
        state.copy(until = state.until.copy(value = date, showDatePicker = false)).addFutureInformation()
    }

    internal fun onCategoryChanged(category: Category) = state.update { state ->
        state.copy(category = category)
    }

    internal fun onCategoryCreate(name: String) {
        viewModelScope.launch {
            val new = Category("", name = name)
            state.update {
                it.copy(category = new)
            }

            val result = withContext(dispatcher) { addCategoryUseCase(new) }

            when (result) {
                is UseCase.Result.Error -> {
                    state.update {
                        it.copy(category = Category.default, errorMessage = result.throwable.message)
                    }
                }

                is UseCase.Result.Success -> {
                    state.update {
                        it.copy(category = result.data)
                    }
                }
            }

        }
    }

    internal fun onCategoryDelete(category: Category?) {
        (state.value.categories - category).firstOrNull()?.let {
            onCategoryChanged(it)
        }
        category?.let {
            viewModelScope.launch {
                if (deleteCategoryUseCase(category) !is UseCase.Result.Success) {
                    onCategoryChanged(category)
                }
            }
        }
    }

    private fun TransactionUiState.addFutureInformation() =
        copy(futureInformation = buildFutureInformation(this), runsOutShift = buildRunsOutShift(this))

    /**
     * На сколько это правило приближает день, когда деньги кончатся.
     *
     * Считается двумя одинаковыми прогонами по одному и тому же окну — с правилом и без него;
     * иначе сравнивались бы разные горизонты, и разница получалась бы из окна, а не из правила.
     * Само правило из фона убирается по идентификатору: при правке существующего оно иначе
     * посчиталось бы дважды.
     */
    private fun buildRunsOutShift(state: TransactionUiState): RunsOutShift? {
        if (state.amount.toDoubleOrNull() == null || state.date.value == null) return null

        val draft = state.tempTransaction
        val background = others.filterNot { it.id == draft.id }
        if (background.isEmpty()) return null

        val window = (background + draft).defaultPeriod()
        val today = today()
        val before = background.simulate(window).findZeroEvents().second?.takeIf { it > today }
        val after = (background + draft).simulate(window).findZeroEvents().second?.takeIf { it > today }

        return when {
            before == null && after == null -> null
            before == null -> RunsOutShift("money starts running out · ${after?.formatted}", worse = true)
            after == null -> RunsOutShift("money no longer runs out in sight", worse = false)
            else -> {
                val days = before.daysUntil(after)
                when {
                    days == 0 -> null
                    days < 0 -> RunsOutShift(
                        "money runs out ${-days} days earlier · ${after.formatted}",
                        worse = true,
                    )

                    else -> RunsOutShift(
                        "money runs out $days days later · ${after.formatted}",
                        worse = false,
                    )
                }
            }
        }
    }

    private fun buildFutureInformation(
        state: TransactionUiState,
    ): AnnotatedString {
        val currency = state.currency
        return buildAnnotatedString {
            append(
                buildColoredAmount(
                    amount = state.amount, currency = state.currency, sign = state.income
                )
            )

            if (state.period == Transaction.Period.OneTime) {
                this.append(" on ")
            } else {
                append(" from ")
            }

            append(state.date.value?.formatted ?: today().formatted)

            if (state.period == Transaction.Period.OneTime) {
                return@buildAnnotatedString
            }

            fun proceedSimulate(simulation: Map<LocalDate, List<Transaction>>) {
                append(simulation.count { entry -> entry.value.isNotEmpty() }.toString())
                append(" times,")
                append(
                    " total: "
                )
                append(buildColoredAmount(simulation.flatMap { it.value }
                    .sumOf { transaction -> transaction.amountSigned }, currency))
            }
            if (state.until.value != null) {
                append(" to ")
                append("${state.until.value?.formatted}")
                append(" repeat ")

                proceedSimulate(listOf(state.tempTransaction).run {
                    simulate(state.date.value.orToday, state.until.value)
                })
            } else {
                when (state.period) {
                    Transaction.Period.Month, Transaction.Period.ThreeMonth, Transaction.Period.HalfYear, Transaction.Period.Year -> {
                        this.append(
                            ". In $LARGE_PERIOD_VALUE ${
                                LARGE_PERIOD_UNIT.toString().lowercase()
                            }'s repeat "
                        )
                        proceedSimulate(listOf(state.tempTransaction).run {
                            simulate(
                                state.date.value.orToday, largePeriodAppend(state.date.value.orToday)
                            )
                        })
                    }

                    else -> {
                        this.append(
                            ". In $DEFAULT_PERIOD_VALUE ${
                                DEFAULT_PERIOD_UNIT.toString().lowercase()
                            }'s repeat "
                        )
                        proceedSimulate(listOf(state.tempTransaction).run {
                            simulate(
                                state.date.value.orToday, defaultPeriodAppend(state.date.value.orToday)
                            )
                        })
                    }
                }
            }
        }
    }
}