package ir.ehsannarmani.compose_charts.extensions

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import ir.ehsannarmani.compose_charts.models.Bars

// Vendored from the compose-charts 0.0.17-js4 fork. The original used a
// `context(DrawScope)` context receiver; Kotlin 2.4 requires context parameters
// to be named, so the DrawScope is passed as a named context parameter and
// Dp.toPx() is resolved through it.
context(drawScope: DrawScope)
fun Path.addRoundRect(
    rect: Rect,
    radius: Bars.Data.Radius,
) = with(drawScope) {
    when (radius) {
        is Bars.Data.Radius.None -> {
            addRect(rect)
        }

        is Bars.Data.Radius.Circular -> {
            addRoundRect(
                roundRect = RoundRect(
                    rect = rect,
                    cornerRadius = CornerRadius(
                        x = radius.radius.toPx(),
                        y = radius.radius.toPx()
                    )
                )
            )
        }

        is Bars.Data.Radius.Rectangle -> {
            addRoundRect(
                roundRect = RoundRect(
                    rect = rect,
                    topLeft = CornerRadius(
                        x = radius.topLeft.toPx(),
                        y = radius.topLeft.toPx()
                    ),
                    topRight = CornerRadius(
                        x = radius.topRight.toPx(),
                        y = radius.topRight.toPx()
                    ),
                    bottomLeft = CornerRadius(
                        x = radius.bottomLeft.toPx(),
                        y = radius.bottomLeft.toPx()
                    ),
                    bottomRight = CornerRadius(
                        x = radius.bottomRight.toPx(),
                        y = radius.bottomRight.toPx()
                    ),
                )
            )
        }
    }
}
