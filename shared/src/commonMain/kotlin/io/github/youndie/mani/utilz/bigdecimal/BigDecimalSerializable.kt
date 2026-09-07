package io.github.youndie.mani.utilz.bigdecimal

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.serialization.Serializable

typealias BigDecimalSerializable =
    @Serializable(with = BigDecimalSerializer::class)
    BigDecimal
