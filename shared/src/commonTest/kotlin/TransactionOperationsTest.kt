@file:OptIn(ExperimentalTime::class)

import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import ru.workinprogress.feature.transaction.Transaction
import ru.workinprogress.feature.transaction.amountSigned
import ru.workinprogress.feature.transaction.createDates
import ru.workinprogress.feature.transaction.defaultPeriod
import ru.workinprogress.feature.transaction.defaultPeriodAppend
import ru.workinprogress.feature.transaction.findZeroEvents
import ru.workinprogress.feature.transaction.simulate
import ru.workinprogress.feature.transaction.toChartInternal
import ru.workinprogress.utilz.bigdecimal.sumOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

class TransactionOperationsTest {

    @Test
    fun testCreateDates() {
        val results = createDates(LocalDate(2000, 1, 1), LocalDate(2000, 1, 4))
        assertEquals(3, results.size)
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun testDefaultPeriod() {
        val results = listOf(
            Transaction(
                "",
                100.0.toBigDecimal(),
                true,
                LocalDate(2000, 1, 1),
                null,
                Transaction.Period.OneTime,
                "Start"
            )
        ).defaultPeriod()

        assertEquals(
            (kotlin.time.Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault()).date.monthNumber + 3) % 12,
            results.second.monthNumber
        )

        assertEquals(
            2000,
            results.first.year
        )

        assertEquals(
            2026,
            results.second.year
        )
    }

    @Test
    fun testSimulateSimple() {
        val result = listOf(
            Transaction(
                "",
                100.0.toBigDecimal(),
                true,
                LocalDate(2000, 1, 1),
                null,
                Transaction.Period.OneTime,
                "Start"
            ),
            Transaction(
                "",
                100.0.toBigDecimal(),
                false,
                LocalDate(2000, 1, 5),
                null,
                Transaction.Period.OneTime,
                "Start"
            ),
            Transaction(
                "TAG",
                1000.0.toBigDecimal(),
                true,
                LocalDate(2000, 1, 1),
                LocalDate(2000, 1, 17),
                Transaction.Period.Week,
                "3 Times 1000"
            ),
        ).simulate(LocalDate(2000, 1, 1) to LocalDate(2000, 3, 1))

        assertEquals(5, result.flatMap { it.value }.size)
        assertEquals(1000.0.toBigDecimal(), result[LocalDate(2000, 1, 8)]?.firstOrNull()?.amount)
        assertEquals(
            3000.0.toBigDecimal(),
            result.flatMap { it.value }.sumOf { transaction -> transaction.amountSigned })
    }

    @Test
    fun testSimulateRepeats() {
        val result = listOf(
            Transaction(
                "",
                1000.0.toBigDecimal(),
                true,
                LocalDate(2000, 1, 1),
                null,
                Transaction.Period.Month,
                "3 Times 1000"
            ),
        ).simulate(LocalDate(2000, 1, 1) to defaultPeriodAppend(LocalDate(2000, 1, 1)))

        assertEquals(3, result.flatMap { it.value }.size)
        assertEquals(result[LocalDate(2000, 1, 1)]?.firstOrNull()?.amount, 1000.0.toBigDecimal())
        assertEquals(result[LocalDate(2000, 2, 1)]?.firstOrNull()?.amount, 1000.0.toBigDecimal())
        assertEquals(result[LocalDate(2000, 3, 1)]?.firstOrNull()?.amount, 1000.0.toBigDecimal())
    }

    @Test
    fun testChartSimple() {
        val now = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        val result = listOf(
            Transaction(
                "",
                100.0.toBigDecimal(),
                true,
                now.minus(1, DateTimeUnit.WEEK),
                null,
                Transaction.Period.OneTime,
                "Start"
            ),
            Transaction(
                "TAG",
                1000.0.toBigDecimal(),
                true,
                now.minus(1, DateTimeUnit.WEEK),
                now.plus(1, DateTimeUnit.WEEK),
                Transaction.Period.Week,
                "3 Times 1000"
            ),
        ).toChartInternal()

        assertEquals(3100.0.toBigDecimal(), result.days.values.last())

    }

    @Test
    fun testZeroEventsNone() {
        val (positive, negative) = listOf(
            Transaction(
                "",
                100.0.toBigDecimal(),
                true,
                LocalDate(2000, 1, 2),
                null,
                Transaction.Period.OneTime,
                ""
            ), Transaction(
                "",
                100.0.toBigDecimal(),
                true,
                LocalDate(2000, 1, 3),
                null,
                Transaction.Period.OneTime,
                ""
            ),
            Transaction(
                "",
                100.0.toBigDecimal(),
                false,
                LocalDate(2000, 1, 3),
                null,
                Transaction.Period.OneTime,
                ""
            ),
            Transaction(
                "",
                100.0.toBigDecimal(),
                true,
                LocalDate(2000, 2, 3),
                null,
                Transaction.Period.OneTime,
                ""
            )
        ).simulate(
            LocalDate(2000, 1, 1)
                    to LocalDate(2000, 3, 1)
        ).findZeroEvents()

        assertTrue(positive == null)
        assertTrue(negative == null)
    }

    @Test
    fun testZeroEventsPositive() {
        val targetDate = LocalDate(2000, 1, 4)
        val (positive, negative) = listOf(
            Transaction(
                "",
                100.0.toBigDecimal(),
                false,
                LocalDate(2000, 1, 2),
                null,
                Transaction.Period.OneTime,
                ""
            ), Transaction(
                "",
                100.0.toBigDecimal(),
                true,
                LocalDate(2000, 1, 3),
                null,
                Transaction.Period.OneTime,
                ""
            ),
            Transaction(
                "",
                100.0.toBigDecimal(),
                true,
                targetDate,
                null,
                Transaction.Period.OneTime,
                ""
            ),
            Transaction(
                "",
                100.0.toBigDecimal(),
                true,
                LocalDate(2000, 2, 3),
                null,
                Transaction.Period.OneTime,
                ""
            )
        ).simulate(
            LocalDate(2000, 1, 1)
                    to LocalDate(2000, 3, 1)
        ).findZeroEvents()

        assertEquals(positive, targetDate)
        assertTrue(negative == null)
    }

    @Test
    fun testZeroEventsNegative() {
        val targetDate = LocalDate(2000, 1, 5)
        val (positive, negative) = listOf(
            Transaction(
                "",
                100.0.toBigDecimal(),
                true,
                LocalDate(2000, 1, 2),
                null,
                Transaction.Period.OneTime,
                ""
            ), Transaction(
                "",
                100.0.toBigDecimal(),
                true,
                LocalDate(2000, 1, 3),
                null,
                Transaction.Period.OneTime,
                ""
            ),
            Transaction(
                "",
                100.0.toBigDecimal(),
                false,
                LocalDate(2000, 1, 4),
                null,
                Transaction.Period.OneTime,
                ""
            ),
            Transaction(
                "",
                200.0.toBigDecimal(),
                false,
                targetDate,
                null,
                Transaction.Period.OneTime,
                ""
            ),
            Transaction(
                "",
                100.0.toBigDecimal(),
                true,
                LocalDate(2000, 1, 1),
                null,
                Transaction.Period.OneTime,
                ""
            )
        ).simulate(
            LocalDate(2000, 1, 1)
                    to LocalDate(2000, 3, 1)
        ).findZeroEvents()

        assertEquals(negative, targetDate)
        assertTrue(positive == null)
    }

    @Test
    fun testZeroEventDateIsCrossingDayNotRuleStart() {
        val ruleStart = LocalDate(2000, 1, 1)

        val (positive, negative) = listOf(
            Transaction(
                "",
                300.0.toBigDecimal(),
                true,
                ruleStart,
                null,
                Transaction.Period.OneTime,
                "Savings"
            ),
            Transaction(
                "",
                100.0.toBigDecimal(),
                false,
                ruleStart,
                null,
                Transaction.Period.Week,
                "Weekly spend"
            ),
        ).simulate(ruleStart to LocalDate(2000, 3, 1)).findZeroEvents()

        // 1 янв: +300 −100 = 200, 8 янв: 100, 15 янв: 0, 22 янв: −100 — знак меняется здесь,
        // а не 1 января, когда правило завели.
        assertEquals(LocalDate(2000, 1, 22), negative)
        assertTrue(positive == null)
    }

    @Test
    fun testZeroEvents() {
        val targetPositive = LocalDate(2000, 2, 3)
        val targetNegative = LocalDate(2000, 1, 5)

        val (positive, negative) = listOf(
            Transaction(
                "",
                100.0.toBigDecimal(),
                true,
                LocalDate(2000, 1, 2),
                null,
                Transaction.Period.OneTime,
                ""
            ), Transaction(
                "",
                100.0.toBigDecimal(),
                true,
                LocalDate(2000, 1, 3),
                null,
                Transaction.Period.OneTime,
                ""
            ),
            Transaction(
                "",
                100.0.toBigDecimal(),
                false,
                LocalDate(2000, 1, 4),
                null,
                Transaction.Period.OneTime,
                ""
            ),
            Transaction(
                "",
                100.0.toBigDecimal(),
                false,
                targetNegative,
                null,
                Transaction.Period.OneTime,
                ""
            ),
            Transaction(
                "",
                1000.0.toBigDecimal(),
                true,
                targetPositive,
                null,
                Transaction.Period.OneTime,
                ""
            )
        ).simulate(
            LocalDate(2000, 1, 1)
                    to LocalDate(2000, 3, 1)
        ).findZeroEvents()

        assertEquals(targetPositive, positive)
        assertEquals(targetNegative, negative)
    }
}