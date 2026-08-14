package ru.workinprogress.mani

import androidx.compose.ui.window.ComposeUIViewController

/**
 * Точка входа для Swift: имя видно из `iosApp` как `MainViewControllerKt.MainViewController()`,
 * и переименование сломало бы вызов на той стороне.
 */
@Suppress("ktlint:standard:function-naming")
fun MainViewController() = ComposeUIViewController { App() }
