package ru.workinprogress.feature.auth.ui.component

import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import ru.workinprogress.feature.auth.ui.model.AuthComponentUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthComponentTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun authComponentTest() {
        val stateFlow = MutableStateFlow(
            AuthComponentUiState(
                title = "AuthTest",
                username = "",
                password = "",
                buttonText = "Submit",
                errorMessage = null,
                loading = false,
            ),
        )

        runComposeUiTest {
            setContent {
                val state = stateFlow.collectAsState()

                AuthComponentImpl(
                    state = state.value,
                    onUsernameChanged = {
                        stateFlow.update { state ->
                            state.copy(username = it)
                        }
                    },
                    onPasswordChanged = {
                        stateFlow.update { state ->
                            state.copy(password = it)
                        }
                    },
                    onButtonClicked = {
                        stateFlow.update { state ->
                            state.copy(loading = true)
                        }
                    },
                )
            }

            onNodeWithTag("title").isDisplayed()
            onNodeWithTag("username").isDisplayed()
            onNodeWithTag("password").isDisplayed()
            onNodeWithTag("login").isDisplayed()
            onNodeWithTag("login").assertTextEquals("Submit")

            val targetUsername = "TESTER"
            onNodeWithTag("username").performTextInput(targetUsername)
            onNodeWithTag("username").assertTextEquals("Username", targetUsername)
            assertEquals(targetUsername, stateFlow.value.username)

            val targetPassword = "password"
            onNodeWithTag("password").performTextInput(targetPassword)
            assertEquals(targetPassword, stateFlow.value.password)

            onNodeWithTag("login").performClick()
            assertTrue(stateFlow.value.loading)

            stateFlow.update { state ->
                state.copy(loading = false, errorMessage = "Error!")
            }

            onNodeWithTag("errorMessage").assertTextEquals("Error!")
        }
    }
}
