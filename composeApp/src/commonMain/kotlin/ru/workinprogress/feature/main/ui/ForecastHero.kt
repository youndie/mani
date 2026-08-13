package ru.workinprogress.feature.main.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
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
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 16.dp)
            .testTag("futureInfo"),
    ) {
        when (state) {
            ForecastUiState.Loading -> ForecastShimmer()

            ForecastUiState.Empty ->
                Headline("Nothing to forecast yet", modifier = Modifier.testTag("forecastEmpty"))

            is ForecastUiState.Steady -> {
                Eyebrow("Balance today")
                Headline(state.balanceToday, modifier = Modifier.testTag("forecastBalance"))
                Caption("no zero crossing in the next three months")
            }

            is ForecastUiState.RunsOut -> {
                Eyebrow("Money runs out")
                Headline(state.runsOutOn, modifier = Modifier.testTag("forecastDate"))
                Caption(
                    "in ${state.daysLeft} days · balance today ",
                    highlight = state.balanceToday,
                )
            }
        }
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
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        modifier = modifier.padding(top = 6.dp),
        style =
            MaterialTheme.typography.displaySmall.copy(
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
) {
    val accent = MaterialTheme.colorScheme.onSurface

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
