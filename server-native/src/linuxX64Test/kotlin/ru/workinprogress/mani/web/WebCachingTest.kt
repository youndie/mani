package ru.workinprogress.mani.web

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Имена взяты из настоящего бандла (`composeApp/build/dist/wasmJs/productionExecutable`), а не
 * придуманы: ошибиться здесь значит закэшировать на год файл, имя которого не меняется, —
 * и выкатом это уже не чинится, только переименованием.
 */
class WebCachingTest {
    @Test
    fun contentHashedNamesAreImmutable() {
        assertTrue("6e23e5428398b92da386.wasm".isContentHashed())
        assertTrue("f824ddb540856b5904cb.wasm".isContentHashed())
    }

    @Test
    fun stableNamesAreNot() {
        // Тот самый случай, ради которого правило перестало смотреть на расширение.
        assertFalse("skiko.wasm".isContentHashed())
        assertFalse("mani.js".isContentHashed())
        assertFalse("skiko.mjs".isContentHashed())
        assertFalse("index.html".isContentHashed())
        assertFalse("styles.css".isContentHashed())
    }

    @Test
    fun shortHexOrNonHexIsNot() {
        assertFalse("abc123.wasm".isContentHashed(), "коротко для хеша")
        assertFalse("zzzzzzzzzzzzzzzzzzzz.wasm".isContentHashed(), "не шестнадцатеричное")
        assertFalse("noextension".isContentHashed(), "без расширения имени файла нет")
    }
}
