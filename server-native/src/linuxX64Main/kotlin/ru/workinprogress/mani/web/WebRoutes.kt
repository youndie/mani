package ru.workinprogress.mani.web

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.defaultForFilePath
import io.ktor.server.request.acceptEncoding
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.utils.io.writeFully
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem

private const val CHUNK = 64 * 1024
private const val IMMUTABLE = "public, max-age=31536000, immutable"

/** Столько шестнадцатеричных цифр webpack даёт своим именам; короче — не хеш, а обычное имя. */
private const val HASH_LENGTH = 16

/**
 * Навсегда кэшируется только то, чьё **имя** зависит от содержимого.
 *
 * Правило «расширение `.wasm` — значит immutable» выглядит верным и неверно: в бандле лежит
 * `skiko.wasm` с постоянным именем рядом с `6e23e5428398b92da386.wasm`. Пометив первый
 * неизменяемым на год, мы получили бы браузеры, до которых обновление Compose не доезжает
 * вовсе, — и починить это выкатом было бы нельзя, только сменой имени файла.
 *
 * Поэтому проверяется имя, а не расширение: имя из одних шестнадцатеричных цифр webpack даёт
 * ровно тем файлам, которые пересобираются под новым именем при любой правке.
 */
internal fun String.isContentHashed(): Boolean {
    val name = substringBeforeLast('.', "")
    return name.length >= HASH_LENGTH && name.all { it in '0'..'9' || it in 'a'..'f' }
}

/**
 * Отдача wasm-приложения тем же сервером — как и на JVM, только вручную.
 *
 * Сжатия здесь нет и быть не может: `ktor-server-compression` публикуется только под JVM. Вместо
 * него отдаются **заранее сжатые** файлы: если рядом лежит `<файл>.gz` и клиент принимает gzip,
 * уходит он. Сжатие переехало в сборку образа, где делается один раз, а не на каждый запрос.
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

        val acceptsGzip = call.request.acceptEncoding()?.contains("gzip") == true
        val wanted = requested.ifEmpty { "index.html" }

        // SPA: неизвестный путь отдаёт оболочку, дальше маршрутизирует само приложение.
        val asset =
            assets.find(wanted, acceptsGzip)
                ?: assets.find("index.html", acceptsGzip)
                ?: return@get call.respond(HttpStatusCode.NotFound)

        call.response.header(HttpHeaders.ETag, asset.etag)
        call.response.header(
            HttpHeaders.CacheControl,
            if (asset.name.isContentHashed()) IMMUTABLE else "no-cache",
        )
        if (asset.gzipped) call.response.header(HttpHeaders.ContentEncoding, "gzip")

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
