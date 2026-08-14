package ru.workinprogress.feature.chart.ui.model

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import ru.workinprogress.feature.chart.ChartResponse
import ru.workinprogress.feature.currency.Currency
import ru.workinprogress.mani.emptyImmutableMap
import ru.workinprogress.mani.today
import ru.workinprogress.utilz.bigdecimal.BigDecimalSerializable

@Serializable
data class ChartUi(
    val days: ImmutableMap<LocalDate, BigDecimalSerializable> = persistentMapOf(),
    val from: LocalDate = LocalDate(2000, 1, 1),
    val to: LocalDate = LocalDate(2001, 4, 1),
    val currency: Currency = Currency.Usd,
    val loading: Boolean = false,
    val todayIndexProvider: () -> Int = { days.entries.indexOfFirst { entry -> entry.key == today() } },
) {
    companion object {
        val Loading = ChartUi(emptyImmutableMap(), today(), today(), Currency.Usd, true)

        operator fun invoke(chart: ChartResponse, currency: Currency): ChartUi =
            ChartUi(chart.days.toImmutableMap(), chart.from, chart.to, currency)
    }
}
