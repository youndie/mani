package ru.workinprogress.feature.chart

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import ru.workinprogress.feature.chart.ui.model.ChartUi
import ru.workinprogress.feature.currency.data.CurrentCurrencyRepository
import ru.workinprogress.feature.transaction.Transaction
import ru.workinprogress.feature.transaction.domain.TransactionRepository
import ru.workinprogress.feature.transaction.toChartInternal
import ru.workinprogress.mani.defaultMinDate

class ChartViewModel(currencyRepository: CurrentCurrencyRepository, transactionRepository: TransactionRepository) :
    ViewModel() {

    private val currency = currencyRepository.currency

    val observe = transactionRepository.dataStateFlow
        .filter(List<Transaction>::isNotEmpty)
        .map { toChart(transactions = it, from = defaultMinDate) }
        .flowOn(Dispatchers.Default)

    private fun toChart(transactions: List<Transaction>, from: LocalDate): ChartUi = ChartUi(
        transactions.toChartInternal()
            .let { chart -> chart.copy(days = chart.days.filter { it.key > from }) },
        currency,
    )
}
