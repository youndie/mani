package io.github.youndie.mani.web

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.defaultForFilePath
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.acceptEncodingItems
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.utils.io.writeFully
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem

private const val CHUNK = 64 * 1024

/**
 * Кодировки, которые клиент принимает. `gzip;q=0` — это отказ, а не согласие: простая проверка
 * «строка содержит gzip» отдала бы сжатое как раз тому, кто его запретил.
 */
internal fun ApplicationRequest.acceptedEncodings(): Set<String> = acceptEncodingItems()
    .filter { it.quality > 0.0 }
    .map { it.value.lowercase() }
    .toSet()

/**
 * Отдача wasm-приложения тем же сервером — как и на JVM, только вручную.
 *
 * Сжатия здесь нет и быть не может: `ktor-server-compression` публикуется только под JVM. Вместо
 * него отдаются **заранее сжатые** файлы: если рядом лежит `<файл>.br` или `<файл>.gz` и клиент
 * принимает эту кодировку, уходит он. Сжатие переехало в сборку образа, где делается один раз,
 * а не на каждый запрос, — и потому на максимальном уровне.
 *
 * Маршрут регистрируется последним и ловит всё оставшееся, поэтому API он не перехватывает.
 */
fun Route.webRoutes(assets: WebAssets) {
    get("/{path...}") {
        val requested =
            call.parameters
                .getAll("path")
                .orEmpty()
                .joinToString("/")

        // Выход за корень каталога: '..' не должен уводить к чужим файлам.
        if (requested.split('/').any { it == ".." }) {
            return@get call.respond(HttpStatusCode.NotFound)
        }

        val accepted = call.request.acceptedEncodings()
        val wanted = requested.ifEmpty { "index.html" }

        // SPA: неизвестный путь отдаёт оболочку, дальше маршрутизирует само приложение.
        val asset =
            assets.find(wanted, accepted)
                ?: assets.find("index.html", accepted)
                ?: return@get call.respond(HttpStatusCode.NotFound)

        call.response.header(HttpHeaders.ETag, asset.etag)
        call.response.header(HttpHeaders.CacheControl, cacheControlFor(asset.name))
        // Тело по одному и тому же пути зависит от `Accept-Encoding` — промежуточный кэш должен
        // это знать, иначе отдаст brotli тому, кто просил файл как есть.
        call.response.header(HttpHeaders.Vary, HttpHeaders.AcceptEncoding)
        asset.encoding?.let { call.response.header(HttpHeaders.ContentEncoding, it) }

        if (call.request.headers[HttpHeaders.IfNoneMatch]
                ?.split(",")
                ?.any { it.trim() == asset.etag } == true
        ) {
            return@get call.respond(HttpStatusCode.NotModified)
        }

        call.respondBytesWriter(ContentType.defaultForFilePath(asset.name), contentLength = asset.size) {
            val chunk = ByteArray(CHUNK)
            SystemFileSystem.source(asset.path).buffered().use { source ->
                while (true) {
                    val read = source.readAtMostTo(chunk, 0, chunk.size)
                    if (read <= 0) break
                    writeFully(chunk, 0, read)
                    flush()
                }
            }
        }
    }
}
