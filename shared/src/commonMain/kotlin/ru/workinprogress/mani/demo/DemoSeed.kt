package ru.workinprogress.mani.demo

import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import ru.workinprogress.feature.transaction.Category
import ru.workinprogress.feature.transaction.Transaction
import ru.workinprogress.mani.today
import ru.workinprogress.utilz.bigdecimal.BigDecimalSerializable

/**
 * Правило песочницы до того, как у него появились идентификаторы.
 *
 * Категория здесь — имя, а не [Category]: на сервере категории заводятся в документе пользователя
 * и получают идентификаторы только в момент создания, поэтому связать правило с категорией можно
 * лишь по имени. Дата задана смещением в днях от «сегодня», а не константой: сид должен давать
 * одинаковую картину в любой день, когда его развернут.
 */
data class DemoRule(
    val comment: String,
    val amount: BigDecimalSerializable,
    val income: Boolean,
    val period: Transaction.Period,
    val startsInDays: Int,
    val category: String,
)

private const val SAVINGS = "Savings"
private const val FOOD = "Food"
private const val HOME = "Home"
private const val BILLS = "Bills"
private const val HEALTH = "Health"

/**
 * Данные, которые видит посетитель, нажавший «Try the demo».
 *
 * Сид подобран под одно требование: баланс должен **уходить в минус внутри горизонта прогноза**
 * (три месяца от сегодня) и уходить не завтра, иначе главная строка экрана — дата, когда деньги
 * кончатся, — окажется либо пустой, либо бессмысленной. Ровно это и проверяет
 * `DemoSeedTest`: без него любая правка сумм тихо превратит витрину в «no zero events», как
 * это уже было с общим аккаунтом `tester`.
 *
 * История начинается за два месяца до сегодня, чтобы экран истории не открывался пустым, а
 * накопления заведены разовым доходом в её начале: собственного понятия «стартовый баланс»
 * в модели нет, баланс считается суммой правил от самой ранней даты.
 */
object DemoSeed {
    const val HISTORY_STARTS_DAYS_AGO = 60

    val rules: List<DemoRule> =
        listOf(
            DemoRule("Savings", 9500.toBigDecimal(), true, Transaction.Period.OneTime, -HISTORY_STARTS_DAYS_AGO, SAVINGS),
            DemoRule("Groceries", 145.toBigDecimal(), false, Transaction.Period.Week, -59, FOOD),
            DemoRule("Rent", 1450.toBigDecimal(), false, Transaction.Period.Month, -58, HOME),
            DemoRule("Utilities", 130.toBigDecimal(), false, Transaction.Period.Month, -55, HOME),
            DemoRule("Phone plan", 25.toBigDecimal(), false, Transaction.Period.Month, -52, BILLS),
            DemoRule("Subscriptions", 45.toBigDecimal(), false, Transaction.Period.Month, -47, BILLS),
            DemoRule("Dentist", 340.toBigDecimal(), false, Transaction.Period.OneTime, 18, HEALTH),
        )

    /** Категории в порядке, в котором их заводить: он же определяет порядок чипов в форме. */
    val categories: List<String> = rules.map(DemoRule::category).distinct()

    /**
     * Разворачивает сид в транзакции — для симуляции и тестов.
     *
     * Идентификаторы здесь синтетические: настоящие раздаёт хранилище при создании, а расчёту
     * прогноза они безразличны.
     */
    fun transactions(today: LocalDate = today()): List<Transaction> =
        rules.mapIndexed { index, rule ->
            Transaction(
                id = index.toString(),
                amount = rule.amount,
                income = rule.income,
                date = today.plus(rule.startsInDays, DateTimeUnit.DAY),
                until = null,
                period = rule.period,
                comment = rule.comment,
                category = Category(rule.category, rule.category),
            )
        }
}
