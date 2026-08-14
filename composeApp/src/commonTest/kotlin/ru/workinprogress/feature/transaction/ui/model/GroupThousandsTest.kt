package ru.workinprogress.feature.transaction.ui.model

import kotlin.test.Test
import kotlin.test.assertEquals

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
