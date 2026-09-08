package io.github.youndie.mani.feature.transaction.ui.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Разряды в суммах.
 *
 * Функция маленькая, а случаев у неё четыре, и три из них — края: знак не должен уезжать
 * внутрь числа, дробная часть не должна разбиваться на группы, короткое число не должно
 * меняться вовсе. Разделитель — неразрывный пробел: обычный переносит сумму на две строки.
 */
class GroupThousandsTest {
    private val nbsp = '\u00A0'

    @Test
    fun shortNumbersAreUntouched() {
        assertEquals("0", groupThousands("0"))
        assertEquals("999", groupThousands("999"))
    }

    @Test
    fun thousandsAreSeparated() {
        assertEquals("1${nbsp}000", groupThousands("1000"))
        assertEquals("4${nbsp}895", groupThousands("4895"))
        assertEquals("1${nbsp}234${nbsp}567", groupThousands("1234567"))
    }

    @Test
    fun signStaysInFront() {
        assertEquals("-1${nbsp}450", groupThousands("-1450"))
    }

    @Test
    fun fractionIsNotGrouped() {
        assertEquals("12${nbsp}345.6789", groupThousands("12345.6789"))
    }
}
