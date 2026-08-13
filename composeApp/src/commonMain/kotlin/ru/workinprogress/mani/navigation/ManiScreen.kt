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

fun ManiScreen.title() = when (this) {
    ManiScreen.Main -> "Home"
    ManiScreen.Add -> "Add transaction"
    ManiScreen.Transaction -> "Edit transaction"
    ManiScreen.History -> "History"
    ManiScreen.Signup -> "Mani"
    else -> ""
}