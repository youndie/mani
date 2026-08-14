@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)

package ru.workinprogress.feature.transaction.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.module.rememberKoinModules
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.parameter.parametersOf
import org.koin.dsl.bind
import org.koin.dsl.module
import ru.workinprogress.feature.transaction.Category
import ru.workinprogress.feature.transaction.Transaction
import ru.workinprogress.feature.transaction.ui.AddTransactionViewModel
import ru.workinprogress.feature.transaction.ui.BaseTransactionViewModel
import ru.workinprogress.feature.transaction.ui.EditTransactionViewModel
import ru.workinprogress.feature.transaction.ui.component.model.TransactionAction
import ru.workinprogress.feature.transaction.ui.component.model.TransactionAction.*
import ru.workinprogress.feature.transaction.ui.model.NegativeColor
import ru.workinprogress.feature.transaction.ui.model.TransactionUiState
import ru.workinprogress.feature.transaction.ui.model.buildColoredAmount
import ru.workinprogress.feature.transaction.ui.model.stringResource
import ru.workinprogress.feature.transaction.ui.utils.CurrencyVisualTransformation
import ru.workinprogress.mani.components.LoadingButton
import ru.workinprogress.mani.navigation.TransactionRoute
import ru.workinprogress.mani.theme.LocalManiFonts
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Composable
fun AddTransactionComponent(onNavigateBack: () -> Unit) {
    rememberKoinModules {
        listOf(
            module {
                viewModelOf(::AddTransactionViewModel).bind<BaseTransactionViewModel>()
            },
        )
    }

    TransactionComponentImpl(null, onNavigateBack)
}

@Composable
fun EditTransactionComponent(transactionRoute: TransactionRoute, onNavigateBack: () -> Unit) {
    rememberKoinModules {
        listOf(
            module {
                viewModel { parameters ->
                    EditTransactionViewModel(
                        transactionId = parameters.get(),
                        get(),
                        get(),
                        get(),
                        get(),
                        get(),
                        get(),
                        get(),
                    )
                }.bind<BaseTransactionViewModel>()
            },
        )
    }

    TransactionComponentImpl(transactionRoute.id, onNavigateBack)
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun <T> ChipsSelector(
    items: ImmutableCollection<T>,
    selected: T,
    expanded: Boolean,
    onExpanded: () -> Unit = { },
    onSelected: (T) -> Unit,
    deleteEnabled: (T) -> Boolean = { false },
    showCreateNew: Boolean = false,
    onCreateNew: () -> Unit = {},
    onDelete: (T) -> Unit = {},
    labelValue: @Composable (T) -> String = { it.toString() },
) {
    val markToDelete = remember { mutableStateOf<T?>(null) }

    FlowRow(
        horizontalArrangement = spacedBy(8.dp),
        modifier = Modifier.animateContentSize(),
    ) {
        items.forEach { item ->
            key(item) {
                val inputChipInteractionSource = remember { MutableInteractionSource() }
                Box {
                    InputChip(
                        onClick = { },
                        selected = selected == item,
                        label = {
                            Text(labelValue(item))
                        },
                        // Галочка у выбранного: заливка на тёмном фоне различима не всегда, и
                        // выбор приходилось искать глазами.
                        leadingIcon = if (selected == item) {
                            {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(InputChipDefaults.IconSize),
                                )
                            }
                        } else {
                            null
                        },
                        elevation = InputChipDefaults.inputChipElevation(
                            elevation = if (item ==
                                markToDelete.value
                            ) {
                                8.dp
                            } else {
                                0.dp
                            },
                        ),
                        trailingIcon = {
                            if (item == markToDelete.value) {
                                IconButton(
                                    {
                                        markToDelete.value = null
                                    },
                                    Modifier.size(AssistChipDefaults.IconSize),
                                    interactionSource = inputChipInteractionSource,
                                ) {
                                    Icon(
                                        Icons.Filled.Clear,
                                        contentDescription = "delete",
                                    )
                                }
                            }
                        },
                        interactionSource = inputChipInteractionSource,
                    )
                    Box(
                        modifier = Modifier.matchParentSize().combinedClickable(
                            onLongClick = {
                                if (deleteEnabled(item)) {
                                    onSelected(item)
                                    markToDelete.value = item
                                }
                            },
                            onClick = {
                                if (markToDelete.value == null || item != markToDelete.value) {
                                    markToDelete.value = null
                                    onSelected(item)
                                } else {
                                    onDelete(item)
                                }
                            },
                            interactionSource = inputChipInteractionSource,
                            indication = null,
                        ),
                    )
                }
            }
        }

        if (showCreateNew) {
            // Пунктирная рамка, как в макете: «New» — это не ещё одна категория в ряду, а место,
            // где её можно завести. Сплошная рамка ставила его вровень с остальными.
            val outline = MaterialTheme.colorScheme.outline

            // Рисовать по границам модификатора нельзя: у чипа они включают область нажатия в
            // 48dp, и рамка получалась выше самого чипа. Отступ считается от его собственной
            // высоты.
            val chipHeight = with(LocalDensity.current) { AssistChipDefaults.Height.toPx() }

            AssistChip(
                onClick = onCreateNew,
                label = { Text("New") },
                border = null,
                modifier = Modifier.drawBehind {
                    val inset = ((size.height - chipHeight) / 2f).coerceAtLeast(0f)
                    drawRoundRect(
                        color = outline,
                        topLeft = Offset(0f, inset),
                        size = Size(size.width, size.height - inset * 2),
                        cornerRadius = CornerRadius(8.dp.toPx()),
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                        ),
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "add",
                        Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
            )
        }

        if (!expanded) {
            TextButton(onExpanded) {
                Text("More")
            }
        }
    }
}

@Composable
private fun NewCategoryDialog(
    showCreateCategoryDialog: MutableState<Boolean> = remember { mutableStateOf(false) },
    onCreate: (String) -> Unit,
) {
    var newCategoryName by remember { mutableStateOf("") }

    if (showCreateCategoryDialog.value) {
        AlertDialog(title = { Text("New category") }, text = {
            OutlinedTextField(newCategoryName, {
                newCategoryName = it
            }, modifier = Modifier.padding(vertical = 16.dp), label = { Text("Category name") })
        }, onDismissRequest = {
            newCategoryName = ""
            showCreateCategoryDialog.value = false
        }, confirmButton = {
            TextButton(onClick = {
                onCreate(newCategoryName)
                newCategoryName = ""
                showCreateCategoryDialog.value = false
            }) {
                Text("Create")
            }
        }, dismissButton = {
            TextButton(onClick = {
                newCategoryName = ""
                showCreateCategoryDialog.value = false
            }) {
                Text("Cancel")
            }
        })
    }
}

@Composable
fun CategoryDeleteDialog(showDeleteDialog: Boolean, onDelete: () -> Unit, onDismiss: () -> Unit) {
    if (showDeleteDialog) {
        AlertDialog(
            title = { Text("Delete selected category?") },
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

/** Подпись над полем: моноширинная, в разрядку — служебная метка, а не текст. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FieldLabel(text: String) {
    Text(
        text.uppercase(),
        modifier = Modifier.padding(bottom = 6.dp),
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = LocalManiFonts.current.mono,
            fontWeight = FontWeight.W500,
            letterSpacing = 1.4.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Стрелка в переключателе: вниз — расход, вверх — доход. Галочка о направлении не говорит. */
@Composable
private fun DirectionIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
}

/**
 * Сумма — самое крупное на экране, как в макете.
 *
 * Обычное поле с плавающей меткой уравнивало её с комментарием, хотя правило состоит прежде
 * всего из числа. Подчёркивание рисуется на всю строку, а не под полем: справа на той же линии
 * стоит расшифровка «−340 $ each month», и она часть той же записи.
 */
@Composable
private fun AmountField(
    state: TransactionUiState,
    focusRequester: FocusRequester,
    onAmountChanged: (String) -> Unit,
    onNext: () -> Unit,
) {
    val error = state.amountError
    val underline = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Column {
        FieldLabel("Amount")

        Row(
            modifier = Modifier.fillMaxWidth().drawBehind {
                val y = size.height - 1.dp.toPx()
                drawLine(underline, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
            },
            verticalAlignment = Alignment.Bottom,
        ) {
            // Голое поле, а не `TextField`: у материального свои отступы, из-за которых число
            // отъезжало вправо от собственной подписи, и своя подложка, которой в макете нет.
            val amountStyle = MaterialTheme.typography.displaySmall.copy(
                fontFamily = LocalManiFonts.current.mono,
                fontWeight = FontWeight.W500,
                color = MaterialTheme.colorScheme.onSurface,
            )

            BasicTextField(
                state.amount,
                onAmountChanged,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp)
                    .focusRequester(focusRequester).testTag("amount"),
                textStyle = amountStyle,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { onNext() }),
                visualTransformation = CurrencyVisualTransformation(state.currency),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    if (state.amount.isEmpty()) {
                        Text("0", style = amountStyle, color = MaterialTheme.colorScheme.outline)
                    }
                    inner()
                },
            )

            // Что получится из введённого: сумма со знаком и как часто она повторится. Раньше
            // проверить себя можно было только сохранив правило.
            if (state.amount.isNotBlank() && error == null) {
                Text(
                    run {
                        val periodText = stringResource(state.period.stringResource)
                        val amount = buildColoredAmount(
                            state.amount,
                            currency = state.currency,
                            sign = state.income,
                        )
                        buildAnnotatedString {
                            append(amount)
                            append(" ")
                            // «Every month» → «each month»: подпись читается фразой, а не меткой.
                            append(periodText.replaceFirst("Every", "each").lowercase())
                        }
                    },
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = LocalManiFonts.current.mono,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, bottom = 14.dp).testTag("amountPreview"),
                )
            }
        }

        // Причина — под самим полем: «Create» неактивна, и без объяснения человек гадает, чего
        // от него хотят.
        error?.let {
            Text(
                it,
                modifier = Modifier.padding(top = 6.dp).testTag("amountError"),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = LocalManiFonts.current.mono),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun TransactionComponentImpl(transactionId: String?, onNavigateBack: () -> Unit) {
    val viewModel = koinViewModel<BaseTransactionViewModel>(
        key = transactionId,
        parameters = { parametersOf(transactionId) },
    )
    val state: State<TransactionUiState> = viewModel.observe.collectAsStateWithLifecycle()

    TransactionComponentImpl(state.value, viewModel::onAction, onNavigateBack)
}

@Composable
internal fun TransactionComponentImpl(
    state: TransactionUiState,
    onAction: (TransactionAction) -> Unit,
    onNavigateBack: () -> Unit,
) {
    var showCreateCategoryDialog = remember { mutableStateOf(false) }
    var categoryToRemove = remember { mutableStateOf<Category?>(null) }

    val datePickerState = rememberDatePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableYear(year: Int): Boolean = year > 2022
        },
    )

    val dateUntilPickerState = rememberDatePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = utcTimeMillis.toDate
                val stateDate = state.date.value
                if (date != null && stateDate != null) {
                    return date > stateDate
                }
                return false
            }
        },
    )

    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { lifecycleOwner, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (!state.edit) {
                        coroutineScope.launch {
                            focusRequester.requestFocus()
                        }
                    }
                }

                Lifecycle.Event.ON_STOP -> {
                }

                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(state.success) {
        if (state.success) {
            onNavigateBack()
        }
    }

    LaunchedEffect(state.date.showDatePicker) {
        if (!state.date.showDatePicker && state.amount.isNotEmpty()) {
            focusManager.clearFocus()
        }
    }

    NewCategoryDialog(showCreateCategoryDialog) {
        onAction(CategoryCreate(it))
    }

    CategoryDeleteDialog(
        showDeleteDialog = categoryToRemove.value != null,
        onDelete = {
            onAction(CategoryDelete(categoryToRemove.value))
            categoryToRemove.value = null
        },
        onDismiss = {
            categoryToRemove.value = null
        },
    )

    // Прокручивается только верхняя часть; полоса с итогом и кнопкой прижата к низу экрана, как
    // в макете. Раньше она ехала следом за полями, и под ней оставалась полоска фона —
    // выглядело как недорисованный блок.
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
        ) {
            // Поля лежат на общем фоне экрана: в макете приподнята только нижняя полоса с итогом, а
            // карточка вокруг всей формы делала из неё отдельный предмет внутри экрана.
            Column(
                modifier = Modifier.widthIn(max = 640.dp).align(Alignment.CenterHorizontally)
                    .padding(horizontal = 20.dp).padding(top = 8.dp),
                verticalArrangement = spacedBy(20.dp),
            ) {
                // Порядок блоков — как в макете и как человек думает о правиле: сначала знак
                // (трачу или получаю), потом сколько, потом как часто, потом с какого дня и по
                // какой, и лишь в конце — необязательная категория.
                //
                // Расход или доход чекбоксом не задаётся: выбор из двух равноправных вариантов
                // читается переключателем, а включённый по умолчанию «Income» ещё и врал про
                // частоту — расходы вносят чаще.
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().testTag("income")) {
                    SegmentedButton(
                        selected = !state.income,
                        onClick = { onAction(IncomeChanged(false)) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.testTag("expense"),
                        icon = { DirectionIcon(Icons.Filled.KeyboardArrowDown) },
                    ) {
                        Text("Expense")
                    }
                    SegmentedButton(
                        selected = state.income,
                        onClick = { onAction(IncomeChanged(true)) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = { DirectionIcon(Icons.Filled.KeyboardArrowUp) },
                    ) {
                        Text("Income")
                    }
                }

                AmountField(
                    state = state,
                    focusRequester = focusRequester,
                    onAmountChanged = { onAction(AmountChanged(it)) },
                    onNext = { onAction(ToggleDatePicker) },
                )

                // Повторяемость показывается сразу: это ядро продукта, и прятать её до выбора
                // даты означало прятать то, чем mani отличается от списка трат.
                Column(Modifier.testTag("periodContainer")) {
                    FieldLabel("Repeats")

                    ChipsSelector(
                        state.periods,
                        state.period,
                        state.periodsExpanded,
                        { onAction(ExpandPeriodClicked) },
                        { onAction(PeriodChanged(it)) },
                    ) { item ->
                        stringResource(item.stringResource)
                    }
                }

                // Начало и конец — одна пара, поэтому в одной строке: «с какого дня и по какой»
                // читается вместе. Для разовой траты второго поля нет, но место под него
                // остаётся — строка не прыгает при смене повторяемости.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        FieldLabel("Starts")
                        TransactionDatePicker(
                            value = state.date.value?.formatted,
                            placeholder = "pick a day",
                            modifier = Modifier.testTag("date"),
                            datePickerState = datePickerState,
                            showDialog = state.date.showDatePicker,
                            onToggleDatePicker = { onAction(ToggleDatePicker) },
                            onDateSelected = { onAction(DateSelected(it)) },
                        )
                    }

                    if (state.period != Transaction.Period.OneTime) {
                        Column(modifier = Modifier.weight(1f)) {
                            FieldLabel("Until")
                            TransactionDatePicker(
                                modifier = Modifier.testTag("until"),
                                value = state.until.value?.formatted,
                                // Пустое «до» — это не пропущенное поле, а «повторять без конца».
                                placeholder = "forever",
                                datePickerState = dateUntilPickerState,
                                showDialog = state.until.showDatePicker,
                                onToggleDatePicker = { onAction(ToggleUntilDatePicker) },
                                onDateSelected = { onAction(DateUntilSelected(it)) },
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().testTag("categoryContainer"),
                ) {
                    FieldLabel("Category")

                    ChipsSelector(
                        state.categories,
                        state.category,
                        state.categoriesExpanded,
                        { onAction(ExpandCategoryClicked) },
                        { onAction(CategoryChanged(it)) },
                        showCreateNew = true,
                        deleteEnabled = {
                            it != Category.default
                        },
                        onCreateNew = {
                            showCreateCategoryDialog.value = true
                        },
                        onDelete = {
                            categoryToRemove.value = it
                        },
                    ) { it.name }
                }
            }

            Column(modifier = Modifier.widthIn(max = 640.dp).align(Alignment.CenterHorizontally)) {
                Spacer(modifier = Modifier.height(16.dp))

                val keyboardController = LocalSoftwareKeyboardController.current

                // Однострочное поле без плавающей метки: комментарий — это одно короткое название
                // вроде «Dining out», а не абзац, и подпись под полем объясняет, где оно всплывёт.
                OutlinedTextField(
                    state.comment,
                    { onAction(CommentChanged(it)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("comment"),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                    singleLine = true,
                    placeholder = { Text("Dining out") },
                )

                Text(
                    "comment — shown in the feed",
                    modifier = Modifier.padding(start = 22.dp, top = 6.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = LocalManiFonts.current.mono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Итог и кнопка — одной полосой: сколько раз повторится и во что обойдётся, читается
        // прямо над тем действием, которое это подтверждает.
        Column(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Полоса тянется во всю ширину — это низ экрана, — но содержимое её стоит в той же
            // колонке, что и поля: иначе на широком окне итог и кнопка уезжали к левому краю,
            // отдельно от формы, к которой относятся.
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = spacedBy(12.dp),
            ) {
                AnimatedVisibility(state.amount.isNotBlank() && state.date.value != null) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.secondary) {
                        Text(
                            state.futureInformation,
                            modifier = Modifier.testTag("futureInformation"),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = LocalManiFonts.current.mono,
                            ),
                        )
                    }
                }

                // Главное последствие правила — не его собственная сумма, а то, на сколько оно
                // сдвигает день, когда деньги кончатся. Без этой строки цену решения приходилось
                // узнавать, сохранив его и вернувшись на главный экран.
                state.runsOutShift?.let { shift ->
                    Text(
                        shift.text,
                        modifier = Modifier.testTag("runsOutShift"),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = LocalManiFonts.current.mono,
                        ),
                        color = if (shift.worse) {
                            NegativeColor
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }

                state.errorMessage?.let {
                    Text(
                        it,
                        modifier = Modifier.testTag("errorMessage"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                LoadingButton(
                    Modifier.fillMaxWidth().testTag("submit"),
                    loading = state.loading,
                    enabled = state.valid,
                    if (state.edit) "Save" else "Create",
                ) { onAction(SubmitClicked) }
            }
        }
    }
}

/** «20 Aug 2026»: в макете дата написана словом — «08/20/2026» ещё и читается по-разному в мире. */
val LocalDate.formatted
    get() = this.format(
        LocalDate.Format {
            dayOfMonth(Padding.NONE)
            char(' ')
            monthName(MonthNames.ENGLISH_ABBREVIATED)
            char(' ')
            year()
        },
    )

inline val Long?.toDate
    get() = this?.let {
        Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
