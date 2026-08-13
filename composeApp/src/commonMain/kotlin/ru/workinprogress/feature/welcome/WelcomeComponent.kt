package ru.workinprogress.feature.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
            Row(
                modifier = Modifier.padding(bottom = 32.dp),
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
 * Как выглядит прогноз — до того, как человек что-то ввёл.
 *
 * Кривая считается из [DemoSeed] тем же кодом, что и настоящий прогноз, поэтому это не картинка
 * «для красоты»: ровно это и появится на экране после «Try the demo».
 */
@Composable
private fun SampleForecast() {
    val chart = remember {
        ChartUi(DemoSeed.transactions().toChartInternal(), Currency.Usd)
    }

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

        Fact("Runs on", "Android · iOS · desktop · web")
        Fact("Server", server ?: "asking…", testTag = "serverBuild")
    }
}

@Composable
private fun Fact(
    label: String,
    value: String,
    testTag: String? = null,
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
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = LocalManiFonts.current.mono),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = testTag?.let { Modifier.testTag(it) } ?: Modifier,
        )
    }
}
