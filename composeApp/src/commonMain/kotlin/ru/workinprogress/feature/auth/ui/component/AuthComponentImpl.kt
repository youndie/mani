package ru.workinprogress.feature.auth.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview
import ru.workinprogress.feature.auth.ui.model.AuthComponentUiState
import ru.workinprogress.mani.components.LoadingButton

@Composable
@Preview
internal fun AuthComponentImpl(
    modifier: Modifier = Modifier,
    state: AuthComponentUiState = remember { AuthComponentUiState("Auth", "", "", "OK", null, false) },
    onUsernameChanged: (String) -> Unit = {},
    onPasswordChanged: (String) -> Unit = {},
    onButtonClicked: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current

    Box(modifier, contentAlignment = Alignment.Center) {
        Card(Modifier) {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp),
            ) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 16.dp).testTag("title"),
                )

                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = state.username,
                    onValueChange = onUsernameChanged,
                    modifier = Modifier.testTag("username"),
                    enabled = !state.loading,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                    singleLine = true,
                    keyboardActions = KeyboardActions(onNext = {
                        focusManager.moveFocus(FocusDirection.Down)
                    }),
                    label = { Text("Username") },
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.password,
                    onValueChange = onPasswordChanged,
                    modifier = Modifier.testTag("password"),
                    enabled = !state.loading,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        onButtonClicked()
                    }),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("Password") },
                )

                Spacer(Modifier.height(24.dp))

                state.errorMessage?.let {
                    Text(
                        it,
                        modifier = Modifier.padding(horizontal = 8.dp).testTag("errorMessage"),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.height(24.dp))
                }

                Spacer(Modifier.height(8.dp))

                LoadingButton(
                    loading = state.loading,
                    buttonText = state.buttonText,
                    modifier = Modifier.align(Alignment.CenterHorizontally).testTag("login"),
                    onButtonClicked = onButtonClicked,
                )

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
