package ru.workinprogress.feature.chart

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import ru.workinprogress.utilz.bigdecimal.BigDecimalSerializable

@Serializable
data class ChartResponse(val days: Map<LocalDate, BigDecimalSerializable>, val from: LocalDate, val to: LocalDate) {
    companion object {
        val Empty =
            ChartResponse(emptyMap(), LocalDate.fromEpochDays(0), LocalDate.fromEpochDays(0))
    }
}
