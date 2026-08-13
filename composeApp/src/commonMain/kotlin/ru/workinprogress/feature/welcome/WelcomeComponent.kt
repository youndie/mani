package ru.workinprogress.feature.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.workinprogress.feature.chart.ui.ChartComponent
import ru.workinprogress.feature.chart.ui.model.ChartUi
import ru.workinprogress.feature.currency.Currency
import ru.workinprogress.feature.main.MainViewModel
import ru.workinprogress.feature.main.ui.ForecastUiState
import ru.workinprogress.feature.transaction.simulate
import ru.workinprogress.feature.transaction.toChartInternal
import ru.workinprogress.mani.demo.DemoSeed
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.module.rememberKoinModules
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import ru.workinprogress.mani.components.LoadingButton
import ru.workinprogress.mani.components.MainAppBarState
import ru.workinprogress.mani.theme.LocalManiFonts

/**
 * Первое, что видит посетитель витрины.
 *
 * До этого им был экран входа с полями логина и пароля — форма, за которой человек, пришедший
 * по ссылке из README, не понимал ни что это, ни зачем ему регистрироваться. Теперь главная
 * дорожка — «попробовать без аккаунта», а вход и регистрация уведены во второй ряд.
 */
@Composable
fun WelcomeComponent(
    appBarState: MainAppBarState,
    onSignInClicked: () -> Unit,
    onSignupClicked: () -> Unit,
    onSuccess: () -> Unit,
) {
    rememberKoinModules {
        listOf(module { viewModelOf(::WelcomeViewModel) })
    }

    val viewModel = koinViewModel<WelcomeViewModel>()
    val state by viewModel.observe.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { appBarState.disable() }

    LaunchedEffect(state.success) {
        if (state.success) onSuccess()
    }

    WelcomeContent(
        state = state,
        onTryDemoClicked = viewModel::onTryDemoClicked,
        onSignInClicked = onSignInClicked,
        onSignupClicked = onSignupClicked,
    )
}

/**
 * Экран без внедрения зависимостей — так его можно снять скриншот-тестом и сверить с макетом.
 */
@Composable
fun WelcomeContent(
    state: WelcomeUiState,
    onTryDemoClicked: () -> Unit = {},
    onSignInClicked: () -> Unit = {},
    onSignupClicked: () -> Unit = {},
) {
    // На ноутбуке витрина — не растянутая узкая колонка, а разворот: слева обещание, справа
    // сразу видно, как выглядит выполненное. Порог взят по левой колонке макета (470) плюс
    // место, на котором график ещё читается.
    BoxWithConstraints {
        if (maxWidth < 900.dp) {
            CompactWelcome(state, onTryDemoClicked, onSignInClicked, onSignupClicked)
        } else {
            WideWelcome(state, onTryDemoClicked, onSignInClicked, onSignupClicked)
        }
    }
}

@Composable
private fun CompactWelcome(
    state: WelcomeUiState,
    onTryDemoClicked: () -> Unit,
    onSignInClicked: () -> Unit,
    onSignupClicked: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .testTag("welcome"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 520.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Logo(Modifier.padding(bottom = 32.dp))

            Text(
                "Know the day the money ends",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.W600,
                    letterSpacing = (-0.9).sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                "Enter your salary, your rent and your subscriptions once. " +
                    "mani projects the balance forward and tells you the date it hits zero.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 28.dp),
            )

            SampleForecast()

            LoadingButton(
                loading = state.loading,
                buttonText = "Try the demo",
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp).testTag("tryDemo"),
                onButtonClicked = onTryDemoClicked,
            )

            Text(
                "your own sandbox — no account, no password",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = LocalManiFonts.current.mono),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            state.errorMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp).testTag("welcomeError"),
                )
            }

            // «или» между демо и аккаунтом: без разделителя вход и регистрация читались как
            // продолжение демо-дорожки, хотя это другой путь.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    "or",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = LocalManiFonts.current.mono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onSignInClicked, modifier = Modifier.weight(1f)) { Text("Sign in") }
                OutlinedButton(onSignupClicked, modifier = Modifier.weight(1f)) { Text("Create account") }
            }

            StackFacts(server = state.server)
        }
    }
}

/**
 * Разворот для широкого экрана: слева обещание, справа образец прогноза.
 *
 * Узкая колонка, растянутая на ноутбук, оставляла половину экрана пустой и заставляла
 * прокручивать до графика — на первом экране это и есть главный довод, и он должен быть виден
 * сразу, рядом с кнопкой.
 */
@Composable
private fun WideWelcome(
    state: WelcomeUiState,
    onTryDemoClicked: () -> Unit,
    onSignInClicked: () -> Unit,
    onSignupClicked: () -> Unit,
) {
    val sample = rememberSample()
    val mono = LocalManiFonts.current.mono

    Column(modifier = Modifier.fillMaxSize().testTag("welcome")) {
        Row(
            modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Logo()
            Spacer(Modifier.weight(1f))
            // Вход и регистрация уведены в шапку: на витрине это путь для тех, у кого аккаунт
            // уже есть, и в основной колонке они спорили бы с «попробовать».
            TextButton(onSignInClicked) { Text("Sign in") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onSignupClicked) { Text("Create account") }
        }

        Row(
            modifier = Modifier.weight(1f).padding(start = 32.dp, end = 32.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.width(470.dp)) {
                Text(
                    "KOTLIN MULTIPLATFORM · ONE CODEBASE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = mono,
                        fontWeight = FontWeight.W500,
                        letterSpacing = 1.5.sp,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )

                Text(
                    "Know the day the money ends",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.W600,
                        letterSpacing = (-1.6).sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 14.dp),
                )

                Text(
                    "Enter your salary, your rent and your subscriptions once. " +
                        "mani projects the balance forward and tells you the date it hits zero.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )

                Row(
                    modifier = Modifier.padding(top = 32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    LoadingButton(
                        loading = state.loading,
                        buttonText = "Try the demo",
                        modifier = Modifier.testTag("tryDemo"),
                        onButtonClicked = onTryDemoClicked,
                    )

                    Text(
                        "your own sandbox\nno account, no password",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = mono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                state.errorMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp).testTag("welcomeError"),
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(top = 36.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )

                Row(
                    modifier = Modifier.padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Fact("Runs on", "Android · iOS · desktop · web", mono = false)
                    Fact("Server", state.server ?: "asking…", testTag = "serverBuild")
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(20.dp))
                    .padding(32.dp)
                    .testTag("sampleForecast"),
            ) {
                Text(
                    "A SAMPLE FORECAST",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = mono,
                        fontWeight = FontWeight.W500,
                        letterSpacing = 1.5.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Дата и баланс — не подписи к картинке, а тот же прогноз, что покажет главный
                // экран после «Try the demo»: считаются тем же кодом из тех же демо-данных.
                Text(
                    sample.headline,
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.W600),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 6.dp).testTag("sampleHeadline"),
                )

                Text(
                    sample.caption,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = mono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )

                ChartComponent(
                    sample.chart,
                    modifier = Modifier.padding(top = 24.dp).height(300.dp),
                    expanded = true,
                )
            }
        }
    }
}

/** Точка и слово: словесный знак, он же — единственная «шапка» витрины. */
@Composable
private fun Logo(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(9.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
        Text(
            "mani",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = LocalManiFonts.current.mono,
                fontWeight = FontWeight.W500,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private data class Sample(
    val chart: ChartUi,
    val headline: String,
    val caption: String,
)

/**
 * Образец прогноза из [DemoSeed] — тем же кодом, что считает настоящий.
 *
 * Нарисовать «красивую» кривую было бы проще, но тогда витрина обещала бы одно, а демо
 * показывало другое.
 */
@Composable
private fun rememberSample(): Sample = remember {
    val transactions = DemoSeed.transactions()
    val chart = ChartUi(transactions.toChartInternal(), Currency.Usd)

    when (val forecast = MainViewModel.buildForecast(transactions.simulate(), Currency.Usd)) {
        is ForecastUiState.RunsOut -> Sample(
            chart = chart,
            headline = forecast.runsOutOn,
            caption = "from ${forecast.balanceToday} today, at the current rules",
        )

        is ForecastUiState.Steady -> Sample(
            chart = chart,
            headline = forecast.balanceToday,
            caption = "no zero crossing in the next three months",
        )

        else -> Sample(chart = chart, headline = "—", caption = "")
    }
}

/**
 * Как выглядит прогноз — до того, как человек что-то ввёл.
 *
 * Кривая считается из [DemoSeed] тем же кодом, что и настоящий прогноз, поэтому это не картинка
 * «для красоты»: ровно это и появится на экране после «Try the demo».
 */
@Composable
private fun SampleForecast() {
    val chart = rememberSample().chart

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
            .padding(vertical = 16.dp, horizontal = 8.dp)
            .testTag("sampleForecast"),
    ) {
        Text(
            "A SAMPLE FORECAST",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = LocalManiFonts.current.mono,
                fontWeight = FontWeight.W500,
                letterSpacing = 1.5.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp),
        )

        ChartComponent(chart, modifier = Modifier.height(160.dp))
    }
}

/**
 * То, ради чего проект вообще интересен, — и именно поэтому строка о сервере берётся из живого
 * ответа `/health`, а не из константы: константа не доказывает ничего.
 */
@Composable
private fun StackFacts(server: String?) {
    val mono = LocalManiFonts.current.mono

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "KOTLIN MULTIPLATFORM · ONE CODEBASE",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = mono,
                fontWeight = FontWeight.W500,
                letterSpacing = 1.5.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Fact("Runs on", "Android · iOS · desktop · web", mono = false)
        Fact("Server", server ?: "asking…", testTag = "serverBuild")
    }
}

/** [mono] — для машинных строк вроде «ktor · kotlin/native · 1.4.2»; перечисление платформ наборное. */
@Composable
private fun Fact(
    label: String,
    value: String,
    testTag: String? = null,
    mono: Boolean = true,
) {
    Column {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = LocalManiFonts.current.mono,
                letterSpacing = 1.2.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.let {
                if (mono) it.copy(fontFamily = LocalManiFonts.current.mono) else it
            },
            color = MaterialTheme.colorScheme.onSurface,
            modifier = testTag?.let { Modifier.testTag(it) } ?: Modifier,
        )
    }
}
