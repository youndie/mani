package ru.workinprogress.feature.transaction.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.valentinilk.shimmer.shimmer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.module.rememberKoinModules
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import ru.workinprogress.feature.main.ui.TransactionDeleteDialog
import ru.workinprogress.feature.main.ui.connectToAppBarState
import ru.workinprogress.feature.main.ui.transactionItems
import ru.workinprogress.feature.transaction.ui.TransactionsViewModel
import ru.workinprogress.feature.transaction.ui.model.TransactionListUiState
import ru.workinprogress.feature.transaction.ui.model.TransactionUiItem
import androidx.compose.ui.unit.sp
import ru.workinprogress.mani.components.MainAppBarState
import ru.workinprogress.mani.theme.LocalManiFonts


@Composable
@Preview
fun TransactionsListComponent(
    modifier: Modifier = Modifier,
    appBarState: MainAppBarState = remember { MainAppBarState() },
    onTransactionClicked: (String) -> Unit = {},
) {
    rememberKoinModules {
        listOf(module {
            viewModelOf(::TransactionsViewModel)
        })
    }

    val viewModel = koinViewModel<TransactionsViewModel>()
    val state: TransactionListUiState by viewModel.observe.collectAsStateWithLifecycle()

    TransactionDeleteDialog(
        state.showDeleteDialog,
        viewModel::onDeleteClicked,
        viewModel::onDismissDeleteDialog
    )

    connectToAppBarState(
        state.selectedTransactions,
        appBarState,
        viewModel::onShowDeleteDialogClicked,
        viewModel::onContextMenuClosed
    )

    TransactionsListContent(state, modifier, appBarState.contextMode, viewModel::onTransactionSelected) {
        onTransactionClicked(it.id)
    }
}

/** История без внедрения зависимостей — чтобы её можно было снять скриншот-тестом. */
@Composable
fun TransactionsListContent(
    state: TransactionListUiState,
    modifier: Modifier = Modifier,
    contextMode: Boolean = false,
    onTransactionSelected: (TransactionUiItem) -> Unit = {},
    onTransactionClicked: (TransactionUiItem) -> Unit = {},
) {
    if (!state.loading && state.data.isEmpty()) {
        TrasactionsEmpty()
    } else {
        BoxWithConstraints(contentAlignment = Alignment.TopCenter) {
        }
        LazyColumn(
            modifier = modifier.widthIn(max = 640.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            // Сводка месяца — то же, что в макете: сколько накопилось с начала месяца и сколько
            // денег сейчас. Без неё история — просто перечень, из которого итог надо считать
            // глазами.
            item {
                MonthSummary(
                    title = state.monthTitle,
                    change = state.monthChange,
                    balance = state.balanceToday,
                    loading = state.loading,
                )
            }

            transactionItems(
                transactions = state.data,
                dayBalances = state.dayBalances,
                selectedTransactions = state.selectedTransactions,
                loading = state.loading,
                contextMode = contextMode,
                onTransactionClicked = onTransactionSelected,
            ) { onTransactionClicked(it) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.transactionsDay(
    dayBalance: String? = null,
    date: LocalDate,
    list: ImmutableList<TransactionUiItem>,
    selectedTransactions: ImmutableList<TransactionUiItem>,
    contextMode: Boolean,
    loadingMode: Boolean,
    onSelected: (TransactionUiItem) -> Unit,
    onClick: (TransactionUiItem) -> Unit
) {
    stickyHeader(date.toString()) {
        val loadingModifier = if (loadingMode) {
            Modifier.shimmer()
        } else {
            Modifier
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .then(loadingModifier)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    date.format(dayHeaderFormat).uppercase().takeIf { !loadingMode } ?: "           ",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.W500,
                        letterSpacing = 1.3.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Баланс на конец дня: без него лента и линия графика говорят о разном, и
                // сверить их глазами нельзя.
                dayBalance?.takeIf { !loadingMode }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
    transactionsListItems(
        list,
        selectedTransactions,
        contextMode,
        loadingMode,
        onSelected,
        onClick
    )
}

private fun LazyListScope.transactionsListItems(
    list: ImmutableList<TransactionUiItem>,
    selectedTransactions: ImmutableList<TransactionUiItem>,
    contextMode: Boolean,
    loadingMode: Boolean,
    onSelected: (TransactionUiItem) -> Unit,
    onClick: (TransactionUiItem) -> Unit
) {
    itemsIndexed(list) { index, transaction ->
        TransactionItem(
            Modifier.testTag(if (index == 0) "transaction" else ""),
            transaction,
            transaction in selectedTransactions,
            contextMode,
            loadingMode,
            onSelected,
            onClick,
        )
    }
}

/** «Sat 15 Aug»: день недели помогает читать ленту как расписание, год в ней лишний. */
private val dayHeaderFormat = LocalDate.Format {
    dayOfWeek(DayOfWeekNames.ENGLISH_ABBREVIATED)
    char(' ')
    dayOfMonth()
    char(' ')
    monthName(MonthNames.ENGLISH_ABBREVIATED)
}

/** Полоса над историей: «August so far» слева, баланс справа. */
@Composable
private fun MonthSummary(
    title: String,
    change: String,
    balance: String,
    loading: Boolean,
) {
    if (loading) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("monthSummary"),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Fact(title, change)
        Fact("Balance", balance)
    }
}

@Composable
private fun Fact(
    label: String,
    value: String,
) {
    Column {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = LocalManiFonts.current.mono),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
