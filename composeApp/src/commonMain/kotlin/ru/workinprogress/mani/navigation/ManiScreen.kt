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
 * Экраны, с которых некуда возвращаться: это корни, а не шаги пути.
 *
 * Главная после входа — именно такой корень, и стрелка «назад» на ней врала: за ней осталась
 * витрина, куда вернуться уже нельзя, — нажатие лишь убирало саму стрелку.
 */
private val rootScreens = setOf(ManiScreen.Preload, ManiScreen.Main, ManiScreen.Welcome)

/**
 * Показывать ли стрелку «назад».
 *
 * Одной записи в стеке мало: на корневом экране под ней может остаться что угодно — граф
 * пересобрался, браузер добавил запись в историю, — и вернуться туда всё равно нельзя.
 */
fun shouldShowBack(screen: ManiScreen, hasPrevious: Boolean): Boolean = hasPrevious && screen !in rootScreens

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
