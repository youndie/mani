package ru.workinprogress.utilz.bigdecimal

import com.ionspin.kotlin.bignum.decimal.BigDecimal

inline fun <T> Iterable<T>.sumOf(selector: (T) -> BigDecimal): BigDecimal {
    var sum = BigDecimal.ZERO
    for (element in this) {
        sum += selector(element)
    }
    return sum
}
