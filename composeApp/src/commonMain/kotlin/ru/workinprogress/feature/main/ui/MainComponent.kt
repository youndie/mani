package ru.workinprogress.feature.main.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.valentinilk.shimmer.ShimmerBounds
import com.valentinilk.shimmer.rememberShimmer
import com.valentinilk.shimmer.shimmer
import kotlinx.collections.immutable.*
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import mani.composeapp.generated.resources.Res
import mani.composeapp.generated.resources.transactions
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.module.rememberKoinModules
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import ru.workinprogress.feature.chart.ui.ChartComponent
import ru.workinprogress.feature.main.MainViewModel
import ru.workinprogress.feature.main.ui.FiltersState.Companion.PAST
import ru.workinprogress.feature.main.ui.FiltersState.Companion.UPCOMING
import ru.workinprogress.feature.transaction.Category
import ru.workinprogress.feature.transaction.ui.component.TrasactionsEmpty
import ru.workinprogress.feature.transaction.ui.component.transactionsDay
import ru.workinprogress.feature.transaction.ui.model.TransactionUiItem
import ru.workinprogress.mani.components.Action
import ru.workinprogress.mani.components.MainAppBarState
import ru.workinprogress.mani.today

@Composable
fun MainComponent(
    appBarState: MainAppBarState,
    snackbarHostState: SnackbarHostState,
    onTransactionClicked: (String) -> Unit,
    onAddTransactionClicked: () -> Unit = {},
) {
    rememberKoinModules {
        listOf(
            module {
                viewModelOf(::MainViewModel)
            },
        )
    }

    val viewModel = koinViewModel<MainViewModel>()
    val state: State<MainUiState> = viewModel.observe.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    connectToAppBarState(
        state.value.selectedTransactions,
        appBarState,
        viewModel::onShowDeleteDialogClicked,
        viewModel::onContextMenuClosed,
    )

    LaunchedEffect(state.value.errorMessage) {
        state.value.errorMessage?.let { string ->
            snackbarHostState.showSnackbar(
                string,
                null,
                false,
                SnackbarDuration.Short,
            )
        }
    }

    DisposableEffect(Unit) {
        val profileAction = Action("Profile", Icons.Default.Person) {
            viewModel.onProfileClicked()
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    coroutineScope.launch {
                        appBarState.showAction(profileAction)
                    }
                }

                Lifecycle.Event.ON_STOP -> {
                    coroutineScope.launch {
                        appBarState.removeAction(profileAction)
                    }
                }

                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AnimatedVisibility(state.value.showProfile) {
        Popup(
            alignment = Alignment.TopEnd,
            onDismissRequest = {
                viewModel.onProfileDismiss()
            },
        ) {
            Card(
                modifier = Modifier.padding(16.dp),
                shape = MaterialTheme.shapes.medium.copy(all = CornerSize(4.dp)),
                colors = CardDefaults.cardColors()
                    .copy(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Column(Modifier.width(IntrinsicSize.Min)) {
                    DropdownMenuItem({ Text("Logout") }, {
                        viewModel.onLogoutClicked()
                    }, modifier = Modifier.testTag("logout"))
                }
            }
        }
    }

    TransactionDeleteDialog(
        showDeleteDialog = state.value.showDeleteDialog,
        onDelete = viewModel::onDeleteClicked,
        onDismiss = viewModel::onDismissDeleteDialog,
    )

    MainContent(
        state.value.transactions,
        state.value.selectedTransactions,
        state.value.filtersState,
        state.value.forecast,
        state.value.dayBalances,
        state.value.showingCacheFrom,
        state.value.loading,
        appBarState.contextMode,
        { onTransactionClicked(it.id) },
        { viewModel.onTransactionSelected(it) },
        { viewModel.onUpcomingToggle(it) },
        { viewModel.onCategorySelected(it) },
        onAddFirstRule = onAddTransactionClicked,
        onFillWithDemoData = viewModel::onFillWithDemoDataClicked,
        unreachable = state.value.unreachable,
        onRetry = viewModel::onRetryClicked,
    )
}

@Composable
private fun <T> DropdownFilterChip(
    items: ImmutableCollection<T>,
    isSelected: Boolean,
    selected: T?,
    itemTitle: (T) -> String = { it.toString() },
    defaultText: String = "",
    showDefault: Boolean = defaultText.isNotEmpty(),
    onSelected: (T?) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }

    ElevatedFilterChip(isSelected, {
        expanded = true
    }, {
        Text(selected?.let { value -> itemTitle(value) } ?: defaultText)

        DropdownMenu(expanded, { expanded = false }) {
            if (showDefault) {
                DropdownMenuItem({
                    Text(defaultText)
                }, {
                    onSelected(null)
                    expanded = false
                })
            }

            items.forEach { item ->
                DropdownMenuItem({
                    Text(itemTitle(item))
                }, {
                    onSelected(item)
                    expanded = false
                })
            }
        }
    }, trailingIcon = {
        Icon(
            Icons.Filled.ArrowDropDown,
            modifier = Modifier.size(AssistChipDefaults.IconSize),
            contentDescription = "dropdown",
        )
    })
}

data class FiltersState(
    val upcoming: Boolean = true,
    val category: Category? = null,
    val categories: ImmutableSet<Category> = persistentSetOf(),
    val periods: ImmutableSet<String> = persistentSetOf(UPCOMING, PAST),
    val loading: Boolean = true,
) {
    companion object {
        const val UPCOMING = "Upcoming"
        const val PAST = "Past"
    }
}

@Composable
internal fun FiltersChips(
    filtersState: FiltersState = FiltersState(),
    modifier: Modifier = Modifier,
    onUpcomingToggle: (Boolean) -> Unit = {},
    onCategorySelected: (Category?) -> Unit = {},
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (filtersState.loading) {
            FilterChip(
                false,
                {},
                modifier = Modifier.testTag("filtersShimmer"),
                label = { Text("   ") },
                enabled = false,
            )
            FilterChip(false, {}, label = { Text("   ") }, enabled = false)
        } else {
            DropdownFilterChip(
                filtersState.periods,
                !filtersState.upcoming,
                if (filtersState.upcoming) UPCOMING else PAST,
            ) { selected ->
                selected?.let {
                    onUpcomingToggle(selected == UPCOMING)
                }
            }
            DropdownFilterChip(
                filtersState.categories,
                filtersState.category != null,
                filtersState.category,
                defaultText = "All categories",
                itemTitle = { it.name },
            ) {
                onCategorySelected(it)
            }
        }
    }
}

@Composable
@Preview
internal fun MainContent(
    transactions: ImmutableMap<LocalDate, ImmutableList<TransactionUiItem>> = persistentMapOf(),
    selectedTransactions: ImmutableList<TransactionUiItem> = persistentListOf(),
    filtersState: FiltersState = FiltersState(),
    forecast: ForecastUiState = ForecastUiState.Loading,
    dayBalances: ImmutableMap<LocalDate, String> = persistentMapOf(),
    showingCacheFrom: String? = null,
    loading: Boolean = false,
    contextMode: Boolean = false,
    onTransactionClicked: (TransactionUiItem) -> Unit = {},
    onTransactionSelected: (TransactionUiItem) -> Unit = {},
    onUpcomingToggle: (Boolean) -> Unit = {},
    onCategorySelected: (Category?) -> Unit = {},
    onAddFirstRule: (() -> Unit)? = null,
    onFillWithDemoData: (() -> Unit)? = null,
    unreachable: ServerUnreachableUiState? = null,
    onRetry: () -> Unit = {},
    // См. TransactionsListContent: день «сегодня» приходит снаружи, чтобы экран не зависел от
    // того, в какой день его снимают.
    today: LocalDate = today(),
    chart: @Composable (
    (
        Boolean,
    ) -> Unit
    ) = remember { @Composable { expanded: Boolean -> ChartComponent(expanded = expanded) } },
) {
    // Сервер не ответил и показать нечего — тогда весь экран об этом, а не лента-заглушка с
    // сообщением в углу.
    if (unreachable != null) {
        ServerUnreachable(unreachable, onRetry = onRetry)
        return
    }

    val filters = remember(filtersState) {
        @Composable {
            FiltersChips(filtersState = filtersState, modifier = Modifier.testTag("filters"), onUpcomingToggle = {
                onUpcomingToggle(it)
            }) {
                onCategorySelected(it)
            }
        }
    }

    val lazyColumnModifier = Modifier.fillMaxSize().testTag("transactions")

    BoxWithConstraints {
        if (maxWidth < 640.dp) {
            LazyColumn(
                modifier = lazyColumnModifier,
                contentPadding = PaddingValues(
                    bottom = with(LocalDensity.current) {
                        WindowInsets.navigationBars.getBottom(this).toDp()
                    } + DefaultFabButtonPadding + DefaultFabButtonPadding + DefaultFabButtonSize,
                ),
            ) {
                item {
                    val handle = LocalPinnableContainer.current?.pin()
                    ForecastAndChart(forecast, expanded = false, showingCacheFrom = showingCacheFrom, chart = chart)
                }

                item {
                    Spacer(Modifier.height(8.dp))
                }

                // Заголовок ленты и фильтры — только когда есть что фильтровать: на первом
                // запуске они стояли над пустотой двумя серыми заглушками.
                if (loading || transactions.isNotEmpty()) {
                    item {
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.secondary) {
                            Text(
                                "Transactions",
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).then(
                                    if (loading) {
                                        Modifier.shimmer()
                                    } else {
                                        Modifier
                                    },
                                ),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        filters()
                    }
                }

                transactionItemsOrEmpty(
                    transactions,
                    selectedTransactions,
                    loading,
                    contextMode,
                    onTransactionClicked,
                    onTransactionSelected,
                    dayBalances,
                    onAddFirstRule,
                    onFillWithDemoData,
                    today,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxHeight().padding(start = 32.dp),
            ) {
                // Ширина ограничена: график тянется по ширине родителя, и без потолка левая
                // колонка забирала всю строку, а списку не оставалось места. 780 и отступ 40 —
                // из макета.
                // По центру вертикали: карточка ниже окна, и прижатая к верху она оставляла под
                // собой пустое поле в половину экрана.
                Column(
                    modifier =
                    Modifier
                        .align(Alignment.CenterVertically)
                        .padding(top = 8.dp, end = 40.dp)
                        .widthIn(max = 780.dp),
                ) {
                    ForecastAndChart(forecast, expanded = true, showingCacheFrom = showingCacheFrom, chart = chart)
                }
                LazyColumn(
                    modifier = lazyColumnModifier,
                    contentPadding = PaddingValues(16.dp),
                ) {
                    if (loading || transactions.isNotEmpty()) {
                        item {
                            filters()
                        }
                    }

                    transactionItemsOrEmpty(
                        transactions,
                        selectedTransactions,
                        loading,
                        contextMode,
                        onTransactionClicked,
                        onTransactionSelected,
                        dayBalances,
                        onAddFirstRule,
                        onFillWithDemoData,
                        today,
                    )

                    item {
                        Spacer(Modifier.height(76.dp))
                    }
                }
            }
        }
    }
}

private fun LazyListScope.transactionItemsOrEmpty(
    transactions: ImmutableMap<LocalDate, ImmutableList<TransactionUiItem>>,
    selectedTransactions: ImmutableList<TransactionUiItem>,
    loading: Boolean,
    contextMode: Boolean,
    onTransactionClicked: (TransactionUiItem) -> Unit,
    onTransactionSelected: (TransactionUiItem) -> Unit,
    dayBalances: ImmutableMap<LocalDate, String>,
    onAddFirstRule: (() -> Unit)?,
    onFillWithDemoData: (() -> Unit)?,
    today: LocalDate,
) {
    if (!loading && transactions.isEmpty()) {
        item {
            TrasactionsEmpty(
                onAddFirstRule = onAddFirstRule,
                onFillWithDemoData = onFillWithDemoData,
                title = null,
            )
        }
    } else {
        transactionItems(
            transactions,
            selectedTransactions = selectedTransactions,
            loading = loading,
            contextMode = contextMode,
            onTransactionClicked = onTransactionClicked,
            onTransactionSelected = onTransactionSelected,
            dayBalances = dayBalances,
            today = today,
        )
    }
}

fun LazyListScope.transactionItems(
    transactions: ImmutableMap<LocalDate, ImmutableList<TransactionUiItem>>,
    dayBalances: ImmutableMap<LocalDate, String> = persistentMapOf(),
    selectedTransactions: ImmutableList<TransactionUiItem>,
    loading: Boolean,
    contextMode: Boolean,
    onTransactionClicked: (TransactionUiItem) -> Unit,
    onTransactionSelected: (TransactionUiItem) -> Unit,
    today: LocalDate = today(),
) {
    transactions.forEach { day ->
        val (date, list) = day
        transactionsDay(
            dayBalance = dayBalances[date],
            date = date,
            list = list,
            selectedTransactions = selectedTransactions,
            contextMode = contextMode,
            loadingMode = loading,
            onSelected = onTransactionSelected,
            onClick = onTransactionClicked,
            today = today,
        )
    }
}

@Composable
fun TransactionDeleteDialog(showDeleteDialog: Boolean, onDelete: () -> Unit, onDismiss: () -> Unit) {
    if (showDeleteDialog) {
        AlertDialog(
            title = { Text("Delete selected transactions?") },
            text = { Text("This action cannot be undone later") },
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
fun connectToAppBarState(
    selected: ImmutableList<TransactionUiItem>,
    appBarState: MainAppBarState,
    onDeleteClicked: () -> Unit,
    onContextMenuClosed: () -> Unit,
) {
    val actions = remember {
        listOf(Action("Delete", Icons.Default.Delete, onDeleteClicked)).toImmutableSet()
    }

    LaunchedEffect(selected) {
        if (selected.isEmpty()) {
            appBarState.closeContextMenu()
        } else {
            appBarState.contextTitle.value = getPluralString(Res.plurals.transactions, selected.size, selected.size)
            appBarState.showContextMenu(actions)
        }
    }

    LaunchedEffect(appBarState.contextMode) {
        if (!appBarState.contextMode) {
            onContextMenuClosed()
        }
    }
}

private val DefaultFabButtonPadding = 16.dp
private val DefaultFabButtonSize = 56.dp

/**
 * Герой и график одним блоком: сначала ответ, потом его обоснование.
 *
 * Обычная функция, а не запомненная лямбда: `remember { @Composable { … } }` прячет от
 * компилятора границы композиции, и разбираться, почему что-то не там, становится негде.
 */
@Composable
private fun ForecastAndChart(
    forecast: ForecastUiState,
    expanded: Boolean,
    showingCacheFrom: String?,
    chart: @Composable (Boolean) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                if (expanded) RoundedCornerShape(16.dp) else RectangleShape,
            )
            .padding(
                start = if (expanded) 28.dp else 24.dp,
                end = if (expanded) 28.dp else 24.dp,
                top = if (expanded) 28.dp else 20.dp,
                bottom = 20.dp,
            ),
    ) {
        showingCacheFrom?.let { takenAt ->
            // Данные не пропали и не устарели по смыслу — правила остаются верными без сети.
            // Сказать, что они последние известные, честнее, чем показать пустой экран.
            Text(
                "No connection · showing data from $takenAt",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 12.dp).testTag("offlineBanner"),
            )
        }

        ForecastHero(forecast, expanded = expanded)

        // На широком экране между подписью и графиком воздуха больше: там герой крупнее, и
        // прежние 18 читались как слипшиеся строки одного блока.
        Spacer(Modifier.height(if (expanded) 28.dp else 18.dp))

        // Настоящий график здесь рисовать нечем, пока нет ни одного правила, — на его месте
        // пунктирная рамка вместо пустоты.
        if (forecast == ForecastUiState.Empty) {
            EmptyForecastPlaceholder()
        } else {
            chart(expanded)
        }
    }
}
