package ru.workinprogress.feature.chart.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import ir.ehsannarmani.compose_charts.LineChart
import ir.ehsannarmani.compose_charts.extensions.format
import ir.ehsannarmani.compose_charts.models.*
import kotlinx.collections.immutable.ImmutableList
import ru.workinprogress.feature.currency.Currency
import ru.workinprogress.feature.transaction.ui.model.formatMoney
import ru.workinprogress.utilz.bigdecimal.BigDecimalSerializable
import kotlin.math.absoluteValue

/**
 * Разворот линии. Был 1200 мс плюс 600 мс задержки заливки плюс каскад по 300 мс на серию —
 * почти две секунды поверх многомегабайтной загрузки веб-сборки. Витрину открывают на десять
 * секунд, и треть из них уходила на пустой прямоугольник.
 */
private const val ANIMATION_MS = 600

private const val CHART_HEIGHT_COMPACT = 220
private const val CHART_HEIGHT_EXPANDED = 320

@Composable
fun ChartImpl(
    values: ImmutableList<BigDecimalSerializable>,
    labels: ImmutableList<String>,
    todayIndexProvider: () -> Int,
    loading: Boolean,
    currency: Currency,
    expanded: Boolean = false,
) {
    val color = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)

    // Разворот проигрывается один раз за жизнь экрана. Дальше данные меняются от фильтров и
    // правок, и каждый раз перерисовывать линию с нуля — не показ, а мигание.
    var alreadyShown by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(ANIMATION_MS.toLong())
        alreadyShown = true
    }

    val data =
        remember(values) {
            listOf(
                Line(
                    label = "Transactions",
                    values = values.map { it.doubleValue(false) },
                    color = SolidColor(color),
                    firstGradientFillColor = color.copy(alpha = .5f),
                    secondGradientFillColor = Color.Transparent,
                    strokeAnimationSpec =
                        if (alreadyShown) tween(0) else tween(ANIMATION_MS, easing = EaseInOutCubic),
                    gradientAnimationDelay = 0,
                    drawStyle = DrawStyle.Stroke(2.dp),
                    curvedEdges = false,
                    dotPointProperties =
                        DotPointProperties(
                            true,
                            color = SolidColor(color),
                            outlineColor = SolidColor(secondary),
                            points = listOf(todayIndexProvider()),
                        ),
                ),
            )
        }

    Card(
        colors =
            CardDefaults
                .cardColors()
                .copy(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        // Ширину задаёт родитель, высоту — раскладка. Пропорция здесь была неуместна: при
        // ограниченной сверху высоте `aspectRatio` возвращает размер, нарушающий ограничения,
        // и карточка наезжала на соседей. Фиксированная высота предсказуема.
        modifier =
            Modifier
                .fillMaxWidth()
                .height((if (expanded) CHART_HEIGHT_EXPANDED else CHART_HEIGHT_COMPACT).dp)
                .border(2.dp, Color.Transparent, RoundedCornerShape(12.dp)),
        elevation = CardDefaults.elevatedCardElevation(2.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(vertical = 12.dp, horizontal = 12.dp),
        ) {
            Crossfade(loading) {
                if (!it) {
                    LineChart(
                        modifier = Modifier.fillMaxSize(),
                        data = data,
                        // Серия здесь одна, и каскад по 300 мс на индекс только откладывал показ.
                        animationMode = AnimationMode.Together(delayBuilder = { 0L }),
                        zeroLineProperties =
                            ZeroLineProperties(
                                enabled = true,
                                color = SolidColor(secondary),
                            ),
                        dividerProperties = DividerProperties(enabled = false),
                        gridProperties =
                            GridProperties(
                                xAxisProperties =
                                    GridProperties.AxisProperties(
                                        thickness = .2.dp,
                                        color = SolidColor(color.copy(alpha = .3f)),
                                        style = StrokeStyle.Dashed(intervals = floatArrayOf(15f, 15f), phase = 10f),
                                    ),
                                yAxisProperties =
                                    GridProperties.AxisProperties(
                                        thickness = .2.dp,
                                        color = SolidColor(color.copy(alpha = .2f)),
                                        style = StrokeStyle.Dashed(intervals = floatArrayOf(15f, 15f), phase = 10f),
                                    ),
                            ),
                        labelProperties =
                            LabelProperties(
                                enabled = true,
                                labels = labels,
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.secondary),
                            ),
                        popupProperties =
                            PopupProperties(
                                textStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.inverseOnSurface),
                                contentBuilder = {
                                    formatMoney(it.toBigDecimal(), currency)
                                },
                                containerColor = MaterialTheme.colorScheme.inverseSurface,
                            ),
                        indicatorProperties =
                            HorizontalIndicatorProperties(
                                enabled = true,
                                contentBuilder = {
                                    it.format(0).compactFormat().orEmpty()
                                },
                                padding = 16.dp,
                                count = IndicatorCount.CountBased(3),
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.secondary),
                            ),
                        labelHelperProperties = LabelHelperProperties(enabled = false),
                        curvedEdges = false,
                    )
                }
            }
        }
    }
}

private fun String?.compactFormat(): String? {
    return this?.toIntOrNull()?.let { number ->
        return if ((number / 1000000).absoluteValue > 1) {
            (number / 1000000).toString() + "m"
        } else if ((number / 1000).absoluteValue > 1) {
            (number / 1000).toString() + "k"
        } else {
            number.toString()
        }
    }
}
