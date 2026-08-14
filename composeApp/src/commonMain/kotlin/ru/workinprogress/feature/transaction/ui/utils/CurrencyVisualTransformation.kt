package ru.workinprogress.feature.transaction.ui.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import ru.workinprogress.feature.currency.Currency

class CurrencyVisualTransformation(val currency: Currency) :
    VisualTransformation,
    OffsetMapping {

    private val currencyString = " ${currency.symbol}"

    override fun filter(text: AnnotatedString) = TransformedText(text + AnnotatedString(currencyString), this)

    override fun originalToTransformed(offset: Int): Int = offset

    override fun transformedToOriginal(offset: Int): Int =
        if (offset - currencyString.length > 0) offset - currencyString.length else 0
}
