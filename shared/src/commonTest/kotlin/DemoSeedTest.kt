import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import ru.workinprogress.feature.transaction.Transaction
import ru.workinprogress.feature.transaction.amountSigned
import ru.workinprogress.feature.transaction.defaultPeriodAppend
import ru.workinprogress.feature.transaction.findZeroEvents
import ru.workinprogress.feature.transaction.simulate
import ru.workinprogress.mani.demo.DemoSeed
import ru.workinprogress.utilz.bigdecimal.sumOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Сид песочницы проверяется не на «данные завелись», а на то, ради чего он существует:
 * посетитель должен увидеть дату, когда деньги кончатся.
 *
 * Ни одно из условий ниже не падает само по себе — суммы можно поменять так, что прогноз тихо
 * выродится в «no zero events», и заметить это будет негде, кроме живого стенда. Поэтому дни
 * считаются от «сегодня», а не от фиксированной даты: тест обязан ловить это в любой день года,
 * включая границы месяцев, где повторение съезжает.
 */
class DemoSeedTest {
    private val today = LocalDate(2026, 8, 13)

    private val simulated get() = DemoSeed.transactions(today).simulate(horizon())

    private fun horizon(): Pair<LocalDate, LocalDate> =
        DemoSeed.transactions(today).minOf(Transaction::date) to defaultPeriodAppend(today)

    private fun balanceAt(date: LocalDate): BigDecimal = simulated
        .filterKeys { it <= date }
        .values
        .flatten()
        .sumOf(Transaction::amountSigned)

    @Test
    fun balanceRunsOutInsideForecastHorizon() {
        val (_, negative) = simulated.findZeroEvents()

        assertNotNull(negative, "сид не уводит баланс в минус — витрина покажет «no zero events»")
        assertTrue(
            negative <= defaultPeriodAppend(today),
            "деньги кончаются $negative, а прогноз считается только до ${defaultPeriodAppend(today)}",
        )
    }

    @Test
    fun balanceTodayIsPositive() {
        val balance = balanceAt(today)

        assertTrue(
            balance > BigDecimal.ZERO,
            "баланс на сегодня $balance: главный экран показывает дату ухода в минус только при положительном балансе",
        )
    }

    @Test
    fun runningOutIsFarEnoughToBeAForecast() {
        val (_, negative) = simulated.findZeroEvents()
        val days = today.daysUntil(assertNotNull(negative))

        assertTrue(days >= 21, "деньги кончаются через $days дн. — это не прогноз, а уведомление")
    }

    @Test
    fun historyIsNotEmpty() {
        val past = simulated.filterKeys { it < today }.values.flatten()

        assertTrue(past.isNotEmpty(), "экран истории откроется пустым")
        assertEquals(
            today.plus(-DemoSeed.HISTORY_STARTS_DAYS_AGO, DateTimeUnit.DAY),
            simulated.keys.min(),
            "история должна начинаться там, где заявлено в сиде",
        )
    }

    @Test
    fun everyRuleHasACategory() {
        assertTrue(DemoSeed.rules.all { it.category.isNotBlank() })
        assertEquals(DemoSeed.categories, DemoSeed.categories.distinct())
    }
}
