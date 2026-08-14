package ru.workinprogress.feature.main.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valentinilk.shimmer.ShimmerBounds
import com.valentinilk.shimmer.rememberShimmer
import com.valentinilk.shimmer.shimmer
import ru.workinprogress.mani.theme.LocalManiFonts

/**
 * Герой главного экрана: одна дата и одно число вместо пятистрочного лога.
 *
 * Порядок здесь — это иерархия, а не вёрстка: сначала подпись «на что смотреть», потом сама дата
 * крупно, потом сколько это в днях и сколько денег есть сейчас. Раньше дата стояла пятой строкой
 * и ничем не отличалась от остальных.
 */
@Composable
fun ForecastHero(
    state: ForecastUiState,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
) {
    // Фон рисует полоса вокруг: на широком экране герой и график лежат в одной карточке, и
    // собственный фон компонента её бы расслоил.
    Column(modifier.fillMaxWidth().testTag("futureInfo")) {
        when (state) {
            ForecastUiState.Loading -> ForecastShimmer()

            // «Nothing to forecast yet» — состояние, а не заголовок: крупным здесь должно стоять
            // то, что человеку делать дальше, иначе самый крупный текст на экране сообщает, что
            // ничего нет.
            ForecastUiState.Empty -> {
                Eyebrow("Nothing to forecast yet")
                Headline("Add what comes in and what goes out", expanded, Modifier.testTag("forecastEmpty"))
            }

            is ForecastUiState.Steady -> {
                Eyebrow("Balance today")
                Headline(state.balanceToday, expanded, Modifier.testTag("forecastBalance"))
                Caption("no zero crossing in the next three months")
            }

            // На широком экране баланс — отдельный факт справа от даты, как в макете: места
            // хватает, и два числа в одной строке подписи там читались бы как одно.
            is ForecastUiState.RunsOut -> {
                Eyebrow("Money runs out")

                if (expanded) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Headline(state.runsOutOn, expanded = true, Modifier.testTag("forecastDate"))
                        BalanceFact(state.balanceToday)
                    }
                    // На широком экране в подписи есть место для второго факта — насколько
                    // глубоко уходит минус. На узком он не помещается и там опущен.
                    if (state.lowestPoint != null && state.lowestOn != null) {
                        Caption(
                            "in ${state.daysLeft} days · lowest point ",
                            highlight = "${state.lowestPoint} on ${state.lowestOn}",
                            highlightColor = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Caption("in ${state.daysLeft} days")
                    }
                } else {
                    Headline(state.runsOutOn, expanded = false, Modifier.testTag("forecastDate"))
                    Caption(
                        "in ${state.daysLeft} days · balance today ",
                        highlight = state.balanceToday,
                    )
                }
            }
        }
    }
}

/**
 * Место графика, пока графика нет: пунктирная рамка с пунктирной же линией вниз.
 *
 * Пустое место сообщало бы, что экран недогрузился. Рамка показывает, что здесь будет, и заодно
 * чем «будет» окажется — линией баланса, идущей к нулю.
 */
@Composable
internal fun EmptyForecastPlaceholder(modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier.fillMaxWidth().height(180.dp).testTag("forecastPlaceholder")) {
        drawRoundRect(
            color = line,
            cornerRadius = CornerRadius(12.dp.toPx()),
            style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
        )

        val inset = size.width * 0.1f
        val width = size.width - inset * 2
        val baseline = size.height * 0.76f

        drawLine(
            color = line,
            start = Offset(inset, baseline),
            end = Offset(inset + width, baseline),
            strokeWidth = 1.dp.toPx(),
        )

        val path = Path()
        listOf(0f to 0.26f, 0.2f to 0.38f, 0.4f to 0.33f, 0.6f to 0.53f, 0.8f to 0.63f, 1f to 0.86f)
            .forEachIndexed { index, point ->
                val (x, y) = point
                val at = Offset(inset + width * x, size.height * y)
                if (index == 0) path.moveTo(at.x, at.y) else path.lineTo(at.x, at.y)
            }

        drawPath(
            path = path,
            color = line,
            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 12f))),
        )
    }
}

/** Баланс на сегодня справа от даты: своя подпись и своё число, набранное как число. */
@Composable
private fun BalanceFact(balance: String) {
    Column(horizontalAlignment = Alignment.End) {
        Eyebrow("Balance today")
        Text(
            balance,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = LocalManiFonts.current.mono,
                fontWeight = FontWeight.W500,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp).testTag("forecastBalanceToday"),
        )
    }
}

/** Подпись над героем. Моноширинный и разрядка — служебная строка, а не текст. */
@Composable
private fun Eyebrow(text: String) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
        Text(
            text.uppercase(),
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontFamily = LocalManiFonts.current.mono,
                    fontWeight = FontWeight.W500,
                    letterSpacing = 1.5.sp,
                ),
        )
    }
}

@Composable
private fun Headline(
    text: String,
    expanded: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        modifier = modifier.padding(top = 6.dp),
        style =
            // На широком экране места больше, и дата — единственное, ради чего туда смотрят.
            (if (expanded) MaterialTheme.typography.displayLarge else MaterialTheme.typography.displaySmall).copy(
                fontWeight = FontWeight.W600,
                letterSpacing = (-0.9).sp,
            ),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Start,
    )
}

/** Пояснение под героем; [highlight] — та часть, что должна читаться как число. */
@Composable
private fun Caption(
    text: String,
    highlight: String? = null,
    highlightColor: Color? = null,
) {
    val accent = highlightColor ?: MaterialTheme.colorScheme.onSurface

    Text(
        buildAnnotatedString {
            append(text)
            highlight?.let {
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.W500)) { append(it) }
            }
        },
        modifier = Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = LocalManiFonts.current.mono),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Заглушка загрузки: три полосы на месте подписи, заголовка и пояснения. */
@Composable
private fun ForecastShimmer() {
    val shimmer = rememberShimmer(ShimmerBounds.Window)
    val bar =
        Modifier
            .shimmer(shimmer)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.extraSmall)
            .testTag("futureInfoShimmer")

    Text("            ", bar, style = MaterialTheme.typography.labelSmall)
    Text("                ", bar.padding(top = 6.dp), style = MaterialTheme.typography.displaySmall)
    Text("                        ", bar.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
}
