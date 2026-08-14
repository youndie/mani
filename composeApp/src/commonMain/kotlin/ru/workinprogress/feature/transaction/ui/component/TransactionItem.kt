package ru.workinprogress.feature.transaction.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.valentinilk.shimmer.ShimmerBounds
import com.valentinilk.shimmer.rememberShimmer
import com.valentinilk.shimmer.shimmer
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import org.jetbrains.compose.resources.stringResource
import ru.workinprogress.feature.transaction.Transaction
import ru.workinprogress.feature.transaction.ui.model.TransactionUiItem
import ru.workinprogress.feature.transaction.ui.model.stringResource
import ru.workinprogress.mani.theme.LocalManiFonts

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionItem(
    modifier: Modifier = Modifier,
    transaction: TransactionUiItem,
    selected: Boolean,
    selectionMode: Boolean,
    loadingMode: Boolean,
    onItemSelected: (TransactionUiItem) -> Unit,
    onItemClicked: (TransactionUiItem) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val containerColor by animateColorAsState(
        if (selected) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            ListItemDefaults.containerColor
        },
    )

    val shimmerInstance = rememberShimmer(shimmerBounds = ShimmerBounds.Window)
    val loadingModifier = if (loadingMode) {
        Modifier.shimmer(shimmerInstance)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.small,
            )
    } else {
        Modifier
    }

    ListItem(
        // Плюс 4dp к материальным 16: заголовки дней и разделитель месяца стоят на 20, и без
        // этого лента шла на четыре точки левее собственных заголовков.
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (selectionMode) {
                        onItemSelected(transaction)
                    } else {
                        onItemClicked(transaction)
                    }
                },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onItemSelected(transaction)
                },
                onLongClickLabel = "Long Click Label",
            )
            .padding(horizontal = 4.dp),
        colors = ListItemDefaults.colors(containerColor = containerColor),
        // Подпись есть только у повторяющегося правила: разовая трата ничем не повторяется, и
        // строка «One time» под каждой такой записью — шум. Значок повторения отличает правило
        // от единичного вхождения быстрее, чем чтение текста.
        supportingContent = if (transaction.period == Transaction.Period.OneTime && !loadingMode) {
            null
        } else {
            {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (!loadingMode) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        buildString {
                            if (loadingMode) return@buildString
                            append(stringResource(transaction.period.stringResource))
                            // «до какого дня» — часть правила, а не отдельная строка: без него
                            // повторяющаяся трата выглядит бесконечной, хотя у неё есть конец.
                            transaction.until?.let { append(" · until ${it.format(dayMonthFormat)}") }
                        },
                        loadingModifier,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = LocalManiFonts.current.mono,
                    )
                }
            }
        },
        trailingContent = {
            // Суммы моноширинным: в списке они стоят колонкой, и разная ширина знака её ломает.
            Text(
                transaction.amountText.takeIf { !loadingMode } ?: AnnotatedString("     "),
                loadingModifier,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.W500,
                fontFamily = LocalManiFonts.current.mono,
            )
        },
        headlineContent = {
            Text(
                transaction.comment.takeIf { !loadingMode } ?: "                  ",
                loadingModifier,
            )
        },
    )
}

/** «28 Mar» — в ленте год не нужен: он уже стоит в разделителе месяца. */
private val dayMonthFormat = LocalDate.Format {
    dayOfMonth(Padding.NONE)
    char(' ')
    monthName(MonthNames.ENGLISH_ABBREVIATED)
}
