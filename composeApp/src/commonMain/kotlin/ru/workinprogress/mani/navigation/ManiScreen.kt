package ru.workinprogress.mani.navigation

enum class ManiScreen {
    Preload,
    Main,
    History,
    Add,
    Transaction,
    Welcome,
    Login,
    Signup,
}

/**
 * Заголовки экранов.
 *
 * Главная названа словесным знаком, а не «Home»: это корневой экран, и в макете там стоит имя
 * продукта. Форма заводит **правило**, а не «транзакцию», — так же, как об этом говорит весь
 * остальной текст приложения.
 */
fun ManiScreen.title() = when (this) {
    ManiScreen.Main -> "mani"
    ManiScreen.Add -> "New rule"
    ManiScreen.Transaction -> "Edit rule"
    ManiScreen.History -> "History"
    ManiScreen.Signup -> "mani"
    else -> ""
}
