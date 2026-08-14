package ir.ehsannarmani.compose_charts.models

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Отметка дня, на котором линия уходит в минус: вертикаль через всё поле и точка на нулевой линии.
 *
 * Добавлено к вендоренной библиотеке, а не нарисовано поверх графика снаружи: координаты точек
 * известны только внутри его канвы — сверху пришлось бы угадывать отступы под подписи осей, и
 * отметка молча разъезжалась бы с линией при любой их правке.
 *
 * @param index индекс точки, с которой значения уходят ниже нуля; `null` — пересечения нет
 * @param markerFill чем заливать кружок изнутри — обычно цвет фона карточки, чтобы линия под ним
 *  не просвечивала
 */
data class ZeroCrossingProperties(
    val enabled: Boolean = false,
    val index: Int? = null,
    val color: Brush = SolidColor(Color.Red),
    val thickness: Dp = 1.dp,
    val style: StrokeStyle = StrokeStyle.Dashed(intervals = floatArrayOf(6f, 10f)),
    val markerRadius: Dp = 5.dp,
    val markerFill: Color = Color.Transparent,
    /** Чем подкрасить площадь под нулевой линией; прозрачный — не красить. */
    val underZeroFill: Color = Color.Transparent,
)
