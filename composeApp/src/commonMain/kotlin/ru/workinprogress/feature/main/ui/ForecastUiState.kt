package ru.workinprogress.feature.main.ui

/**
 * Герой главного экрана — то, ради чего приложение существует.
 *
 * До редизайна это были пять строк моноширинного текста одним `AnnotatedString`: баланс, изменение
 * за сегодня, следующая транзакция, суммы за два месяца и в конце — дата, когда деньги кончатся.
 * Главное стояло последним и выглядело как строка отладочного вывода.
 *
 * Состояния перечислены, а не собраны из необязательных полей: экран «денег хватает» и экран
 * «правил ещё нет» — разные, и раньше их различие пряталось внутри готовой строки, где его не
 * видел ни компонент, ни тест.
 */
sealed interface ForecastUiState {
    /** Данные ещё не пришли. */
    data object Loading : ForecastUiState

    /** Правил нет — прогнозировать нечего. */
    data object Empty : ForecastUiState

    /**
     * Деньги кончатся [runsOutOn] — это и есть заголовок экрана.
     *
     * @param runsOutOn день без года: год очевиден из «через столько-то дней», а место в заголовке дорого
     * @param daysLeft сколько дней осталось
     * @param balanceToday баланс на сегодня, уже с валютой
     */
    data class RunsOut(
        val runsOutOn: String,
        val daysLeft: Int,
        val balanceToday: String,
        /** Самая низкая точка внутри горизонта и её день — «−705 $» и «1 Jan». */
        val lowestPoint: String? = null,
        val lowestOn: String? = null,
    ) : ForecastUiState

    /**
     * Внутри горизонта прогноза баланс в минус не уходит.
     *
     * Отдельное состояние, а не `runsOutOn = null`: показывать нужно другое — сам баланс, а не
     * дату, которой нет.
     */
    data class Steady(val balanceToday: String) : ForecastUiState
}
