package io.github.youndie.mani

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import io.github.youndie.mani.theme.AppTheme
import ru.workinprogress.appframe.AppFrame

@OptIn(ExperimentalFoundationApi::class)
fun main() = application {
    AppTheme {
        AppFrame(
            ::exitApplication,
            title = "Mani",
        ) {
            App(modifier = Modifier.fillMaxSize())
        }
    }
}
