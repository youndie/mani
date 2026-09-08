package io.github.youndie.mani.feature.transaction

import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Границы по одной: правило, отвергающее всё, выглядит рабочим и на верном вводе тоже.
 */
class RulesTest {
    private val rule = Transaction(
        id = "",
        amount = "10".toBigDecimal(),
        income = false,
        date = LocalDate.parse("2026-09-08"),
        until = null,
        period = Transaction.Period.Month,
        comment = "Rent",
        category = Category.default,
    )

    @Test
    fun anOrdinaryRulePasses() {
        assertNull(transactionProblem(rule))
        assertNull(transactionProblem(rule.copy(until = rule.date)))
        assertNull(transactionProblem(rule.copy(income = true)))
    }

    /**
     * Ноль и минус — не придирка.
     *
     * Знак задаёт `income`: `amountSigned` умножает сумму на −1 у расхода, поэтому расход
     * с суммой −100 прибавляет к прогнозу сто. Ноль же не двигает прогноз вовсе.
     */
    @Test
    fun anAmountMustBePositive() {
        assertNotNull(transactionProblem(rule.copy(amount = "0".toBigDecimal())))
        assertNotNull(transactionProblem(rule.copy(amount = "-100".toBigDecimal())))
        assertNull(transactionProblem(rule.copy(amount = "0.01".toBigDecimal())))
    }

    /** Правило, кончающееся раньше начала, не разворачивается ни в один день и молча исчезает. */
    @Test
    fun theEndCannotPrecedeTheStart() {
        assertNotNull(transactionProblem(rule.copy(until = rule.date.minus(1, DateTimeUnit.DAY))))
        assertNull(transactionProblem(rule.copy(until = rule.date)))
    }

    @Test
    fun aCommentHasALimit() {
        assertNull(transactionProblem(rule.copy(comment = "c".repeat(200))))
        assertNotNull(transactionProblem(rule.copy(comment = "c".repeat(201))))
    }

    @Test
    fun aCategoryNeedsAName() {
        assertNotNull(categoryProblem(Category(id = "", name = "")))
        assertNotNull(categoryProblem(Category(id = "", name = "   ")))
        assertNull(categoryProblem(Category(id = "", name = "Food")))
        assertNotNull(categoryProblem(Category(id = "", name = "c".repeat(65))))
    }

    @Test
    fun theProblemNamesWhatToFix() {
        assertEquals(
            "Amount must be greater than zero",
            transactionProblem(rule.copy(amount = "0".toBigDecimal())),
        )
    }
}
