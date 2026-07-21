package ir.ehsannarmani.compose_charts.models

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class DotPointProperties(
    val enabled: Boolean = false,
    val radius: Dp = 3.dp,
    val strokeWidth: Dp = 2.dp,
    val color: SolidColor = SolidColor(Color.Unspecified),
    val outlineColor: SolidColor = SolidColor(Color.Unspecified),
    val points: List<Int> = emptyList(),
)