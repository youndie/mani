package ru.workinprogress.mani.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import ru.workinprogress.mani.components.Action
import ru.workinprogress.mani.components.MainAppBarState
import ru.workinprogress.mani.components.ManiAppBar
import ru.workinprogress.mani.navigation.ManiScreen
import ru.workinprogress.mani.navigation.title
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import ru.workinprogress.feature.chart.ui.ChartComponent
import ru.workinprogress.feature.chart.ui.model.ChartUi
import ru.workinprogress.feature.currency.Currency
import ru.workinprogress.feature.main.ui.FiltersState
import ru.workinprogress.feature.main.ui.ForecastUiState
import ru.workinprogress.feature.main.ui.MainContent
import ru.workinprogress.feature.main.ui.ServerUnreachable
import ru.workinprogress.feature.main.ui.ServerUnreachableUiState
import ru.workinprogress.feature.transaction.Category
import ru.workinprogress.feature.transaction.Transaction
import ru.workinprogress.feature.transaction.ui.component.TransactionComponentImpl
import ru.workinprogress.feature.transaction.ui.component.TransactionsListContent
import ru.workinprogress.feature.transaction.ui.model.TransactionListUiState
import ru.workinprogress.feature.transaction.ui.model.TransactionUiItem
import ru.workinprogress.feature.transaction.ui.model.DateDataUiState
import ru.workinprogress.feature.transaction.ui.model.RunsOutShift
import ru.workinprogress.feature.transaction.ui.model.TransactionUiState
import ru.workinprogress.feature.welcome.WelcomeContent
import ru.workinprogress.feature.welcome.WelcomeUiState
import ru.workinprogress.mani.AddRuleFab
import ru.workinprogress.mani.theme.AppTheme
import ru.workinprogress.viddik.annotations.ViddikScreenshot

/**
 * Снимки экранов для сверки с макетом.
 *
 * Имена латиницей: viddik заменяет не-ASCII на подчёркивания, и кириллические имена схлопнулись
 * бы в почти одинаковые файлы, затирая друг друга.
 *
 * Экраны берутся в **stateless**-виде, без внедрения зависимостей: иначе снимок зависел бы от
 * сети и от того, что лежит в базе, и сверять его с макетом было бы нечем.
 */
private const val WIDTH = 393
private const val HEIGHT = 852

/** Размер из макета R10 — экран ноутбука. */
private const val WIDE_WIDTH = 1280
private const val WIDE_HEIGHT = 820

@Composable
private fun Harness(content: @Composable () -> Unit) {
    AppTheme(darkTheme = true) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            content()
        }
    }
}

private val demoDay = LocalDate(2026, 8, 16)

private fun item(
    comment: String,
    amount: Int,
    income: Boolean,
    period: Transaction.Period,
    until: LocalDate? = null,
) = TransactionUiItem(
    Transaction(
        id = comment,
        amount = amount.toBigDecimal(),
        income = income,
        date = demoDay,
        until = until,
        period = period,
        comment = comment,
        category = Category("1", "Home"),
    ),
    Currency.Usd,
)

/**
 * Снимок для README: приложение целиком — шапка, прогноз, лента и кнопка.
 *
 * Собирается тем же кодом и теми же демо-данными, что и остальные снимки, поэтому картинка в
 * репозитории не разъедется с интерфейсом: обновляется одной командой вместе с голденами.
 */
@ViddikScreenshot(name = "readme", group = "screens", width = WIDE_WIDTH, height = WIDE_HEIGHT)
@Composable
fun ReadmeScreenshot() {
    Harness {
        Box(Modifier.fillMaxSize()) {
            Column {
                ManiAppBar(
                    appbarState = remember {
                        MainAppBarState().apply {
                            title.value = ManiScreen.Main.title()
                            showAction(Action("Profile", Icons.Default.Person) {})
                        }
                    },
                    onBack = {},
                )

                WideHomeContent()
            }

            AddRuleFab(
                onClick = {},
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

/** Кнопка добавления правила: скруглённый квадрат в цвете контейнера, как в макете. */
@ViddikScreenshot(name = "fab", group = "screens", width = 120, height = 120)
@Composable
fun FabScreenshot() {
    Harness {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AddRuleFab(onClick = {})
        }
    }
}

/**
 * Шапка в двух своих видах: на корневом экране — словесный знак, на вложенном — «назад» и
 * название места. Снимок узкий и низкий: проверять тут нечего, кроме самой полосы.
 */
@ViddikScreenshot(name = "app bar", group = "screens", width = WIDTH, height = 160)
@Composable
fun AppBarScreenshot() {
    Harness {
        Column {
            ManiAppBar(
                appbarState = remember {
                    MainAppBarState().apply {
                        title.value = ManiScreen.Main.title()
                        showAction(Action("Profile", Icons.Default.Person) {})
                    }
                },
                onBack = {},
            )

            Spacer(Modifier.height(8.dp))

            ManiAppBar(
                appbarState = remember {
                    MainAppBarState().apply {
                        title.value = ManiScreen.Add.title()
                        showBack.value = true
                        showAction(Action("Profile", Icons.Default.Person) {})
                    }
                },
                onBack = {},
            )
        }
    }
}

@ViddikScreenshot(name = "welcome", group = "screens", width = WIDTH, height = HEIGHT)
@Composable
fun WelcomeScreenshot() {
    Harness {
        WelcomeContent(WelcomeUiState(server = "ktor · kotlin/native · 1.4.2"))
    }
}

/** Витрина на ноутбуке — разворот из макета, а не растянутая узкая колонка. */
@ViddikScreenshot(name = "welcome wide", group = "screens", width = WIDE_WIDTH, height = WIDE_HEIGHT)
@Composable
fun WelcomeWideScreenshot() {
    Harness {
        WelcomeContent(WelcomeUiState(server = "ktor · kotlin/native · 1.4.2"))
    }
}

@ViddikScreenshot(name = "home forecast", group = "screens", width = WIDTH, height = HEIGHT)
@Composable
fun HomeForecastScreenshot() {
    Harness {
        MainContent(
            transactions = mapOf(
                demoDay to persistentListOf(item("Rent", 1450, false, Transaction.Period.Month)),
                LocalDate(2026, 8, 17) to
                    persistentListOf(item("Groceries", 145, false, Transaction.Period.Week)),
            ).toImmutableMap(),
            // Фильтры не в состоянии загрузки: иначе на снимке две пустые заглушки вместо чипов,
            // и сверять с макетом нечего.
            filtersState = FiltersState(
                upcoming = true,
                categories = persistentSetOf(Category("1", "Home"), Category("2", "Food")),
                loading = false,
            ),
            forecast = ForecastUiState.RunsOut("12 October", 60, "4 895 $"),
            dayBalances = mapOf(
                demoDay to "3 445 $",
                LocalDate(2026, 8, 17) to "3 300 $",
            ).toImmutableMap(),
            // График подставляется готовым состоянием: по умолчанию `MainContent` поднимает его
            // через Koin, а снимок не должен зависеть ни от графа, ни от сети.
            chart = { expanded ->
                ChartComponent(
                    ChartUi(
                        days = (0..90)
                            .associate {
                                LocalDate(2026, 7, 15).plus(it, DateTimeUnit.DAY) to
                                    (4895 - it * 55).toBigDecimal()
                            }.toImmutableMap(),
                        currency = Currency.Usd,
                        todayIndexProvider = { 30 },
                    ),
                    expanded = expanded,
                )
            },
        )
    }
}

/** Главная на ноутбуке: слева прогноз, справа лента — как в R4. */
@ViddikScreenshot(name = "home forecast wide", group = "screens", width = WIDE_WIDTH, height = WIDE_HEIGHT)
@Composable
fun HomeForecastWideScreenshot() {
    Harness {
        WideHomeContent()
    }
}

/** Главная с демо-данными — общая для снимка широкой раскладки и картинки в README. */
@Composable
private fun WideHomeContent() {
    MainContent(
            transactions = mapOf(
                demoDay to persistentListOf(item("Rent", 1450, false, Transaction.Period.Month)),
                LocalDate(2026, 8, 17) to
                    persistentListOf(item("Groceries", 145, false, Transaction.Period.Week)),
            ).toImmutableMap(),
            filtersState = FiltersState(
                upcoming = true,
                categories = persistentSetOf(Category("1", "Home"), Category("2", "Food")),
                loading = false,
            ),
            // Дно и его день — из тех же данных, что рисует график ниже: он уходит в минус
            // на 55 в последний день горизонта.
            forecast = ForecastUiState.RunsOut(
                runsOutOn = "12 October",
                daysLeft = 60,
                balanceToday = "4 895 $",
                lowestPoint = "\u221255 $",
                lowestOn = "13 October",
            ),
            dayBalances = mapOf(
                demoDay to "3 445 $",
                LocalDate(2026, 8, 17) to "3 300 $",
            ).toImmutableMap(),
            chart = { expanded ->
                ChartComponent(
                    ChartUi(
                        days = (0..90)
                            .associate {
                                LocalDate(2026, 7, 15).plus(it, DateTimeUnit.DAY) to
                                    (4895 - it * 55).toBigDecimal()
                            }.toImmutableMap(),
                        currency = Currency.Usd,
                        todayIndexProvider = { 30 },
                    ),
                    expanded = expanded,
                )
            },
    )
}

@ViddikScreenshot(name = "home empty", group = "screens", width = WIDTH, height = HEIGHT)
@Composable
fun HomeEmptyScreenshot() {
    Harness {
        MainContent(
            forecast = ForecastUiState.Empty,
            onAddFirstRule = {},
            onFillWithDemoData = {},
        )
    }
}

@ViddikScreenshot(name = "rule form", group = "screens", width = WIDTH, height = HEIGHT)
@Composable
fun RuleFormScreenshot() {
    Harness {
        TransactionComponentImpl(
            // Ровно тот случай, что нарисован в макете: 340 в месяц с 20 августа, без конца.
            state = TransactionUiState(
                amount = "340",
                currency = Currency.Usd,
                period = Transaction.Period.Month,
                date = DateDataUiState(LocalDate(2026, 8, 20)),
                futureInformation = AnnotatedString(
                    "\u2212340 $ from 20 Aug 2026. In 1 year's repeat 12 times, total: \u22124\u00A0080 $"
                ),
                runsOutShift = RunsOutShift("money runs out 12 days earlier \u00B7 19 Nov 2026", worse = true),
            ),
            onAction = {},
        ) {}
    }
}

/** Сервер не ответил и показать нечего: причина, кнопка и обратный отсчёт. */
@ViddikScreenshot(name = "server unreachable", group = "screens", width = WIDTH, height = HEIGHT)
@Composable
fun ServerUnreachableScreenshot() {
    Harness {
        ServerUnreachable(
            ServerUnreachableUiState(
                cause = "HTTP 503 · mani.kotlin.website · 11:42:07",
                retryInSeconds = 8,
            )
        )
    }
}

/** Та же форма с ошибкой в сумме: ноль правило не сдвинет. */
@ViddikScreenshot(name = "rule form error", group = "screens", width = WIDTH, height = HEIGHT)
@Composable
fun RuleFormErrorScreenshot() {
    Harness {
        TransactionComponentImpl(
            state = TransactionUiState(
                amount = "0",
                currency = Currency.Usd,
                period = Transaction.Period.Month,
                date = DateDataUiState(LocalDate(2026, 8, 20)),
            ),
            onAction = {},
        ) {}
    }
}

@ViddikScreenshot(name = "history", group = "screens", width = WIDTH, height = HEIGHT)
@Composable
fun HistoryScreenshot() {
    Harness {
        TransactionsListContent(
            state = TransactionListUiState(
                // Два месяца подряд — иначе разделитель месяца снимком не проверить.
                data = mapOf(
                    LocalDate(2026, 8, 6) to
                        persistentListOf(item("Groceries", 145, false, Transaction.Period.Week)),
                    LocalDate(2026, 8, 5) to
                        persistentListOf(item("Rent", 1450, false, Transaction.Period.Month)),
                    LocalDate(2026, 7, 30) to
                        persistentListOf(item("Salary", 2400, true, Transaction.Period.Month)),
                    // У «Course» есть конец — в ленте он дописывается к строке повторения.
                    LocalDate(2026, 7, 28) to
                        persistentListOf(
                            item("Course", 350, false, Transaction.Period.Month, LocalDate(2027, 3, 28))
                        ),
                ).toImmutableMap(),
                dayBalances = mapOf(
                    LocalDate(2026, 8, 6) to "4 980 $",
                    LocalDate(2026, 8, 5) to "5 125 $",
                    LocalDate(2026, 7, 30) to "6 575 $",
                    LocalDate(2026, 7, 28) to "4 175 $",
                ).toImmutableMap(),
                monthTitle = "August so far",
                monthChange = "+1 110 $",
                balanceToday = "4 980 $",
            ),
        )
    }
}
