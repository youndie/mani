package io.github.youndie.mani.web

import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun onlyTheFileNameDecidesNotTheDirectory() {
        assertTrue("assets/6e23e5428398b92da386.wasm".isContentHashed())
        assertFalse("6e23e5428398b92da386/skiko.wasm".isContentHashed())
    }

    @Test
    fun composeResourcesAreRevalidatedNotKept() {
        // Шрифты и `.cvr` лежат под постоянными именами: неизменяемыми на год их делать нельзя.
        val fonts = "composeResources/mani.composeapp.generated.resources/font"
        assertEquals(CACHE_REVALIDATE, cacheControlFor("$fonts/JetBrainsMono-Regular.ttf"))
        assertEquals(CACHE_REVALIDATE, cacheControlFor("$fonts/IBMPlexSans-SemiBold.ttf"))
        assertEquals(
            CACHE_REVALIDATE,
            cacheControlFor("composeResources/mani.composeapp.generated.resources/values/strings.commonMain.cvr"),
        )
    }

    @Test
    fun hashedBundleIsKeptForAYear() {
        assertEquals(CACHE_IMMUTABLE, cacheControlFor("bfa5198fb2fe683c613a.wasm"))
        assertEquals(CACHE_REVALIDATE, cacheControlFor("skiko.wasm"))
    }
}
