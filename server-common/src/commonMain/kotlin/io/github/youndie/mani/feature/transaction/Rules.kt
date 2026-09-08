package io.github.youndie.mani.feature.transaction

private const val MAX_COMMENT_LENGTH = 200
private const val MAX_CATEGORY_NAME_LENGTH = 64

/**
 * Чем плохо это правило, либо `null`, если ничем.
 *
 * Форма клиента до половины этого не допускает, и всё же проверка стоит здесь. Форма — это
 * удобство, а не граница: за ней стоит открытый HTTP, и «клиент такого не пришлёт» — утверждение
 * о клиенте, а не о сервере.
 *
 * Текст уходит в ответ и показывается человеку, поэтому он на английском и говорит, что
 * исправить.
 */
fun transactionProblem(transaction: Transaction): String? {
    // В локальную переменную, потому что `until` объявлен в `:shared`: умного приведения по
    // публичному свойству из другого модуля компилятор не делает — его значение между проверкой
    // и чтением ничто не удерживает.
    val until = transaction.until

    return when {
        // Знак задаёт `income`, а не сумма. Отрицательная сумма с `income = false` даёт ПЛЮС
        // в прогнозе: `amountSigned` умножает её на −1. Ноль же не двигает прогноз вовсе —
        // правило, не делающее того единственного, ради чего заводится.
        transaction.amount.signum() <= 0 -> "Amount must be greater than zero"

        // Правило, кончающееся раньше, чем начинается, не разворачивается ни в один день:
        // симуляция не найдёт ему места и промолчит. Снаружи это выглядит как исчезнувшая
        // запись.
        until != null && until < transaction.date ->
            "The end date cannot be earlier than the start date"

        transaction.comment.length > MAX_COMMENT_LENGTH ->
            "Comment must be at most $MAX_COMMENT_LENGTH characters long"

        else -> null
    }
}

/** То же для категории: имя показывается в чипах формы, и пустого имени там быть не может. */
fun categoryProblem(category: Category): String? = when {
    category.name.isBlank() -> "Category name cannot be empty"

    category.name.length > MAX_CATEGORY_NAME_LENGTH ->
        "Category name must be at most $MAX_CATEGORY_NAME_LENGTH characters long"

    else -> null
}
