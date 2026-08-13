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
import androidx.compose.runtime.remember
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

private const val CHART_MAX_HEIGHT = 320
private const val CHART_MAX_WIDTH = 496

@Composable
fun ChartImpl(
    values: ImmutableList<BigDecimalSerializable>,
    labels: ImmutableList<String>,
    todayIndexProvider: () -> Int,
    loading: Boolean,
    currency: Currency,
) {
    val color = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)

    val data =
        remember(values) {
            listOf(
                Line(
                    label = "Transactions",
                    values = values.map { it.doubleValue(false) },
                    color = SolidColor(color),
                    firstGradientFillColor = color.copy(alpha = .5f),
                    secondGradientFillColor = Color.Transparent,
                    strokeAnimationSpec = tween(1200, easing = EaseInOutCubic),
                    gradientAnimationDelay = 600,
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
        // Ширина берётся от родителя, а не задаётся числом: карточка в 496 dp на телефоне
        // обрезалась, а в окне ноутбука оставляла мёртвые поля по бокам. Высота ограничена
        // сверху, чтобы на широком экране график не превращался в полосу во весь экран.
        modifier =
            Modifier
                .fillMaxWidth()
                .widthIn(max = CHART_MAX_WIDTH.dp)
                .heightIn(max = CHART_MAX_HEIGHT.dp)
                .aspectRatio(3 / 2f)
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
                        animationMode =
                            AnimationMode.Together(delayBuilder = {
                                it * 300L
                            }),
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
