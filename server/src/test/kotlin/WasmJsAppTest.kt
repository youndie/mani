package io.github.youndie.mani

import io.github.youndie.mani.utilz.wasmJsApp
import io.github.youndie.mani.web.CACHE_IMMUTABLE
import io.github.youndie.mani.web.CACHE_REVALIDATE
import io.github.youndie.mani.web.isContentHashed
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Статика JVM-сборки отвечает с той же политикой, что нативная: сжатие, ETag, `immutable`
 * только по имени-хешу.
 *
 * Файлы настоящие — тот бандл, что `copyFrontend` кладёт в ресурсы. До этой проверки шрифты
 * здесь уходили несжатыми и с `immutable` при постоянном имени.
 */
class WasmJsAppTest {

    @Test
    fun `a font goes gzipped and is revalidated rather than kept`() = static {
        val response = client.get("/$FONT") { header(HttpHeaders.AcceptEncoding, "gzip") }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding])
        assertEquals(CACHE_REVALIDATE, response.headers[HttpHeaders.CacheControl])
        assertTrue(HttpHeaders.AcceptEncoding in response.headers.getAll(HttpHeaders.Vary).orEmpty().joinToString())
        val packed = response.bodyAsBytes().size
        val plain = client.get("/$FONT").bodyAsBytes().size
        assertTrue(packed < plain * 6 / 10, "gzip should take about half of the font, got $packed of $plain")
    }

    @Test
    fun `an unchanged font revalidates to 304`() = static {
        val first = client.get("/$FONT")
        assertNull(first.headers[HttpHeaders.ContentEncoding])
        val etag = assertNotNull(first.headers[HttpHeaders.ETag], "no validator: no-cache would mean a full download")

        val again = client.get("/$FONT") { header(HttpHeaders.IfNoneMatch, etag) }
        assertEquals(HttpStatusCode.NotModified, again.status)
    }

    @Test
    fun `the hashed bundle is kept for a year, the constant-named one is not`() = static {
        val hashed = bundle().first { it.name.endsWith(".wasm") && it.name.isContentHashed() }

        assertEquals(CACHE_IMMUTABLE, client.get("/${hashed.name}").headers[HttpHeaders.CacheControl])
        assertEquals(CACHE_REVALIDATE, client.get("/skiko.wasm").headers[HttpHeaders.CacheControl])
    }

    private fun static(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { routing { wasmJsApp() } }
        block()
    }

    /** Корень бандла в ресурсах: `build/resources/main/static`, куда его кладёт `copyFrontend`. */
    private fun bundle(): List<File> {
        val root = assertNotNull(javaClass.classLoader.getResource("static/index.html"), "no bundle on the classpath")
        return File(root.toURI()).parentFile.listFiles().orEmpty().toList()
    }

    private companion object {
        const val FONT = "composeResources/mani.composeapp.generated.resources/font/JetBrainsMono-Regular.ttf"
    }
}
