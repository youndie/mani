package io.github.youndie.mani.web

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.writeString
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * Выбор представления файла и заголовки, с которыми оно уходит.
 *
 * Содержимое файлов — метки, а не настоящее сжатие: сервер сжатым не занимается, он выбирает
 * готовый файл, и по телу ответа видно, какой именно выбран. Раскладка каталога повторяет
 * настоящую — шрифт Compose лежит под постоянным именем, рядом с ним его `.gz` и `.br`.
 */
class WebRoutesTest {
    private val root = Path(SystemTemporaryDirectory, "mani-web-${Random.nextLong().toULong()}")

    @BeforeTest
    fun layOutBundle() {
        write("index.html", "shell")
        write(FONT, "ttf")
        write("$FONT.gz", "ttf-gzip")
        write("$FONT.br", "ttf-brotli")
        write("bfa5198fb2fe683c613a.wasm", "wasm")
        write("bfa5198fb2fe683c613a.wasm.gz", "wasm-gzip")
    }

    @AfterTest
    fun removeBundle() = deleteRecursively(root)

    @Test
    fun `brotli is preferred when the client takes both`() = web {
        val response = client.get("/$FONT") { header(HttpHeaders.AcceptEncoding, "gzip, deflate, br, zstd") }
        assertEquals("ttf-brotli", response.bodyAsText())
        assertEquals("br", response.headers[HttpHeaders.ContentEncoding])
    }

    @Test
    fun `gzip goes to a client without brotli`() = web {
        val response = client.get("/$FONT") { header(HttpHeaders.AcceptEncoding, "gzip") }
        assertEquals("ttf-gzip", response.bodyAsText())
        assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding])
    }

    @Test
    fun `a refused encoding is not sent`() = web {
        // «Строка содержит br» — неверная проверка: q=0 значит «не присылать».
        val response = client.get("/$FONT") { header(HttpHeaders.AcceptEncoding, "br;q=0, gzip;q=0") }
        assertEquals("ttf", response.bodyAsText())
        assertNull(response.headers[HttpHeaders.ContentEncoding])
    }

    @Test
    fun `without Accept-Encoding the file goes as is`() = web {
        val response = client.get("/$FONT")
        assertEquals("ttf", response.bodyAsText())
        assertNull(response.headers[HttpHeaders.ContentEncoding])
    }

    @Test
    fun `a missing brotli copy falls back to gzip`() = web {
        val response = client.get("/bfa5198fb2fe683c613a.wasm") { header(HttpHeaders.AcceptEncoding, "br, gzip") }
        assertEquals("wasm-gzip", response.bodyAsText())
        assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding])
    }

    @Test
    fun `every response says it depends on Accept-Encoding`() = web {
        assertEquals(HttpHeaders.AcceptEncoding, client.get("/$FONT").headers[HttpHeaders.Vary])
        assertEquals(
            HttpHeaders.AcceptEncoding,
            client.get("/$FONT") { header(HttpHeaders.AcceptEncoding, "br") }.headers[HttpHeaders.Vary],
        )
    }

    @Test
    fun `a font is revalidated, the hashed bundle is kept`() = web {
        assertEquals(CACHE_REVALIDATE, client.get("/$FONT").headers[HttpHeaders.CacheControl])
        assertEquals(CACHE_IMMUTABLE, client.get("/bfa5198fb2fe683c613a.wasm").headers[HttpHeaders.CacheControl])
    }

    @Test
    fun `each representation has its own ETag and revalidates to 304`() = web {
        val brotli = client.get("/$FONT") { header(HttpHeaders.AcceptEncoding, "br") }
        val plain = client.get("/$FONT")
        val brotliTag = brotli.headers[HttpHeaders.ETag]!!
        assertNotEquals(brotliTag, plain.headers[HttpHeaders.ETag])

        val revalidated =
            client.get("/$FONT") {
                header(HttpHeaders.AcceptEncoding, "br")
                header(HttpHeaders.IfNoneMatch, brotliTag)
            }
        assertEquals(HttpStatusCode.NotModified, revalidated.status)
        assertEquals("br", revalidated.headers[HttpHeaders.ContentEncoding])

        // Тег brotli-копии не подтверждает файл как есть: это другое тело.
        val mismatched = client.get("/$FONT") { header(HttpHeaders.IfNoneMatch, brotliTag) }
        assertEquals(HttpStatusCode.OK, mismatched.status)
        assertEquals("ttf", mismatched.bodyAsText())
    }

    private fun web(block: suspend ApplicationTestBuilder.() -> Unit) = runBlocking {
        val assets = WebAssets.scan(root.toString())
        testApplication {
            application { routing { webRoutes(assets) } }
            block()
        }
    }

    private fun write(relative: String, content: String) {
        val path = Path(root, relative)
        path.parent?.let { SystemFileSystem.createDirectories(it) }
        SystemFileSystem.sink(path).buffered().use { it.writeString(content) }
    }

    private fun deleteRecursively(path: Path) {
        val metadata = SystemFileSystem.metadataOrNull(path) ?: return
        if (metadata.isDirectory) SystemFileSystem.list(path).forEach(::deleteRecursively)
        SystemFileSystem.delete(path)
    }

    private companion object {
        const val FONT = "composeResources/mani.composeapp.generated.resources/font/JetBrainsMono-Regular.ttf"
    }
}
