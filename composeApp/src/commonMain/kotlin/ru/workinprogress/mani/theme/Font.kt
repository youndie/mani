package ru.workinprogress.mani.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import mani.composeapp.generated.resources.IBMPlexSans_Regular
import mani.composeapp.generated.resources.IBMPlexSans_SemiBold
import mani.composeapp.generated.resources.JetBrainsMono_Medium
import mani.composeapp.generated.resources.JetBrainsMono_Regular
import mani.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.Font

/**
 * Наборный шрифт: заголовки, текст списка, кнопки.
 *
 * До редизайна весь интерфейс, включая прозу, был набран моноширинным — приложение читалось
 * как отладочный вывод, а не как деньги.
 */
@Composable
fun sansFamily() =
    FontFamily(
        Font(resource = Res.font.IBMPlexSans_Regular, FontWeight.W400),
        Font(resource = Res.font.IBMPlexSans_SemiBold, FontWeight.W600),
    )

/**
 * Моноширинный: суммы, даты, метки и словесный знак.
 *
 * Здесь он не украшение — цифры в списке стоят друг под другом, и разная ширина знака ломает
 * колонку сумм.
 */
@Composable
fun monoFamily() =
    FontFamily(
        Font(resource = Res.font.JetBrainsMono_Regular, FontWeight.W400),
        Font(resource = Res.font.JetBrainsMono_Medium, FontWeight.W500),
    )

/**
 * Пара шрифтов, доступная из любого места дерева.
 *
 * Нужна затем, что моноширинный ставится **точечно** — на суммы и даты внутри компонентов, — а
 * не ролью типографики: роль `bodyMedium` в одном месте держит комментарий к правилу, а в
 * другом его сумму.
 */
data class ManiFonts(
    val sans: FontFamily,
    val mono: FontFamily,
)

/**
 * По умолчанию — системные гарнитуры, а не исключение: `@Preview` и тесты компонентов рисуются
 * без [ru.workinprogress.mani.theme.AppTheme], и падать там не за что. Внутри темы значение
 * всегда подменяется настоящей парой.
 */
val LocalManiFonts =
    staticCompositionLocalOf {
        ManiFonts(sans = FontFamily.Default, mono = FontFamily.Monospace)
    }

/**
 * Метки (`labelSmall`, `labelMedium`) уходят в моноширинный целиком: это подписи-эйбрау вроде
 * «BALANCE TODAY» и «UPCOMING», и они везде служебные. Остальные роли — наборные.
 */
fun createTypography(
    parent: Typography,
    sans: FontFamily,
    mono: FontFamily,
): Typography =
    Typography(
        parent.displayLarge.copy(fontFamily = sans),
        parent.displayMedium.copy(fontFamily = sans),
        parent.displaySmall.copy(fontFamily = sans),
        parent.headlineLarge.copy(fontFamily = sans),
        parent.headlineMedium.copy(fontFamily = sans),
        parent.headlineSmall.copy(fontFamily = sans),
        parent.titleLarge.copy(fontFamily = sans),
        parent.titleMedium.copy(fontFamily = sans),
        parent.titleSmall.copy(fontFamily = sans),
        parent.bodyLarge.copy(fontFamily = sans),
        parent.bodyMedium.copy(fontFamily = sans),
        parent.bodySmall.copy(fontFamily = sans),
        parent.labelLarge.copy(fontFamily = sans),
        parent.labelMedium.copy(fontFamily = mono),
        parent.labelSmall.copy(fontFamily = mono),
    )
