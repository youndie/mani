package io.github.youndie.mani.feature.chart

import androidx.lifecycle.ViewModel
import io.github.youndie.mani.defaultMinDate
import io.github.youndie.mani.feature.chart.ui.model.ChartUi
import io.github.youndie.mani.feature.currency.GetCurrentCurrencyUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * График: те же правила, что и в ленте, только сложенные по дням.
 *
 * Обе зависимости — use case'ы, как у остальных моделей экранов. Раньше эта брала репозиторий
 * валюты напрямую, а объявленный рядом [GetChartUseCase] не использовала вовсе: слой, который
 * весь остальной клиент обязан соблюдать, здесь обходился, и увидеть это можно было только
 * открыв файл.
 */
class ChartViewModel(
    private val getChartUseCase: GetChartUseCase,
    private val getCurrentCurrencyUseCase: GetCurrentCurrencyUseCase,
) : ViewModel() {

    val observe: Flow<ChartUi> = flow {
        val currency = getCurrentCurrencyUseCase.get()

        emitAll(
            getChartUseCase
                .get()
                // Пустой график рисовать нечем: до первой загрузки правил экран показывает
                // состояние загрузки, а не пустые оси.
                .filter { chart -> chart.days.isNotEmpty() }
                .map { chart ->
                    // Горизонт слева обрезается здесь, а не в расчёте: расчёт общий с прогнозом,
                    // а показывать месяц назад — решение этого экрана.
                    ChartUi(chart.copy(days = chart.days.filterKeys { day -> day > defaultMinDate }), currency)
                },
        )
    }.flowOn(Dispatchers.Default)
}
