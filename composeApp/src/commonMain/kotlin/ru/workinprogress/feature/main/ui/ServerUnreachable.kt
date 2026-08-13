package ru.workinprogress.feature.main.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.workinprogress.mani.components.LoadingButton
import ru.workinprogress.mani.theme.LocalManiFonts

/**
 * Что показывать, когда сервер не ответил и показать нечего.
 *
 * @param cause машинная строка причины: «HTTP 503 · api.mani.kotlin.website · 11:42:07».
 *  Она здесь не для красоты — по ней человек отличит «мой вайфай» от «у них всё лежит», а
 *  сообщение об ошибке без кода и адреса не отличает ничего.
 * @param retryInSeconds сколько осталось до автоматической повторной попытки; `null` — если
 *  автоповтора нет.
 */
data class ServerUnreachableUiState(
    val cause: String? = null,
    val retryInSeconds: Int? = null,
)

@Composable
fun ServerUnreachable(
    state: ServerUnreachableUiState,
    modifier: Modifier = Modifier,
    retrying: Boolean = false,
    onRetry: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp).testTag("serverUnreachable"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        InterruptedForecast()

        Text(
            "Can't reach the server",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )

        // Про сохранность правил сказано отдельно: без этого «не удалось загрузить» читается как
        // «данные потеряны», хотя пропала только связь.
        Text(
            "The forecast needs the backend to recalculate. Your rules are safe.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp).widthIn(max = 420.dp),
        )

        state.cause?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = LocalManiFonts.current.mono),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 14.dp).testTag("unreachableCause"),
            )
        }

        LoadingButton(
            loading = retrying,
            buttonText = "Try again",
            icon = Icons.Filled.Refresh,
            modifier = Modifier.padding(top = 28.dp).testTag("retry"),
            onButtonClicked = onRetry,
        )

        state.retryInSeconds?.let {
            Text(
                "retrying automatically in $it s",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = LocalManiFonts.current.mono),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp).testTag("retryCountdown"),
            )
        }
    }
}

/** Линия прогноза, оборвавшаяся на сегодняшнем дне: дальше считать нечем. */
@Composable
private fun InterruptedForecast() {
    val line = MaterialTheme.colorScheme.outlineVariant
    val marker = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface

    Canvas(Modifier.width(200.dp).height(90.dp)) {
        val known = listOf(0f to 0.24f, 0.12f to 0.33f, 0.24f to 0.22f, 0.36f to 0.42f, 0.48f to 0.36f)
        val unknown = listOf(0.52f to 0.38f, 0.64f to 0.49f, 0.76f to 0.44f, 0.88f to 0.62f, 1f to 0.58f)

        fun path(points: List<Pair<Float, Float>>) = Path().apply {
            points.forEachIndexed { index, (x, y) ->
                val at = Offset(size.width * x, size.height * y)
                if (index == 0) moveTo(at.x, at.y) else lineTo(at.x, at.y)
            }
        }

        drawLine(
            color = line,
            start = Offset(0f, size.height * 0.73f),
            end = Offset(size.width, size.height * 0.73f),
            strokeWidth = 1.dp.toPx(),
        )

        drawPath(path(known), line, style = Stroke(width = 2.dp.toPx()))
        drawPath(
            path(unknown),
            line,
            style = Stroke(
                width = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 12f)),
            ),
        )

        val breakPoint = Offset(size.width * 0.48f, size.height * 0.36f)
        drawCircle(surface, radius = 4.dp.toPx(), center = breakPoint)
        drawCircle(marker, radius = 4.dp.toPx(), center = breakPoint, style = Stroke(width = 2.dp.toPx()))
    }
}
