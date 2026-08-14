package ru.workinprogress.mani.security

/**
 * base64url без выравнивания — [RFC 7515 §2](https://www.rfc-editor.org/rfc/rfc7515#section-2).
 *
 * Своя реализация, а не `java.util.Base64`: код общий, и JVM-класс закрыл бы нативную сборку.
 * Алфавит отличается от обычного base64 двумя символами (`-_` вместо `+/`), и `=` не пишется.
 * При подписи это не косметика: лишний `=` меняет байты, которые ушли под подпись.
 */
private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

internal fun ByteArray.encodeBase64Url(): String {
    val out = StringBuilder((size + 2) / 3 * 4)
    var i = 0
    while (i + 2 < size) {
        val n =
            (this[i].toInt() and 0xFF shl 16) or
                (this[i + 1].toInt() and 0xFF shl 8) or
                (this[i + 2].toInt() and 0xFF)
        out.append(ALPHABET[n ushr 18 and 0x3F])
        out.append(ALPHABET[n ushr 12 and 0x3F])
        out.append(ALPHABET[n ushr 6 and 0x3F])
        out.append(ALPHABET[n and 0x3F])
        i += 3
    }
    when (size - i) {
        1 -> {
            val n = this[i].toInt() and 0xFF shl 16
            out.append(ALPHABET[n ushr 18 and 0x3F])
            out.append(ALPHABET[n ushr 12 and 0x3F])
        }

        2 -> {
            val n = (this[i].toInt() and 0xFF shl 16) or (this[i + 1].toInt() and 0xFF shl 8)
            out.append(ALPHABET[n ushr 18 and 0x3F])
            out.append(ALPHABET[n ushr 12 and 0x3F])
            out.append(ALPHABET[n ushr 6 and 0x3F])
        }
    }
    return out.toString()
}

internal fun String.decodeBase64Url(): ByteArray {
    val out = ArrayList<Byte>(length / 4 * 3)
    var buffer = 0
    var bits = 0
    for (ch in this) {
        val value = ALPHABET.indexOf(ch)
        require(value >= 0) { "недопустимый символ в base64url: '$ch'" }
        buffer = buffer shl 6 or value
        bits += 6
        if (bits >= 8) {
            bits -= 8
            out.add((buffer ushr bits and 0xFF).toByte())
        }
    }
    return out.toByteArray()
}

internal fun ByteArray.toHex(): String {
    val digits = "0123456789abcdef"
    val out = StringBuilder(size * 2)
    for (byte in this) {
        val value = byte.toInt() and 0xFF
        out.append(digits[value ushr 4])
        out.append(digits[value and 0x0F])
    }
    return out.toString()
}

/**
 * Сравнение за постоянное время.
 *
 * Обычное `==` на строках выходит из цикла на первом несовпадении, и разница во времени ответа
 * рассказывает, сколько символов угадано. Здесь сравниваются хеши паролей — то место, где такая
 * подсказка дороже всего.
 */
internal fun constantTimeEquals(expected: String, actual: String): Boolean {
    if (expected.length != actual.length) return false
    var diff = 0
    for (i in expected.indices) {
        diff = diff or (expected[i].code xor actual[i].code)
    }
    return diff == 0
}
