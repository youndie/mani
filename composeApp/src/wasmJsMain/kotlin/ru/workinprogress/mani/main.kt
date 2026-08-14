package ru.workinprogress.mani

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.bindToNavigation
import kotlinx.browser.document
import kotlinx.browser.window

@OptIn(ExperimentalComposeUiApi::class, ExperimentalBrowserHistoryApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        LaunchedEffect("") {
            document.getElementById("progressBar")?.let { element ->
                document.body?.removeChild(element)
            }
        }

        App(
            onNavHostReady = { window.bindToNavigation(it) },
        )
    }
}
