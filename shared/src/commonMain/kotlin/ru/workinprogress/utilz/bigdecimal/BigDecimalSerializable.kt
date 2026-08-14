package ru.workinprogress.utilz.bigdecimal

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.serialization.Serializable

typealias BigDecimalSerializable =
    @Serializable(with = BigDecimalSerializer::class)
    BigDecimal
