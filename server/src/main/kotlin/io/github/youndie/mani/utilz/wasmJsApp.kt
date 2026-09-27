package io.github.youndie.mani.utilz

import io.github.youndie.mani.web.cacheControlFor
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.application.install
import io.ktor.server.http.content.ETagProvider
import io.ktor.server.http.content.default
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.conditionalheaders.ConditionalHeaders
import io.ktor.server.response.header
import io.ktor.server.routing.Routing

/**
 * Статика из ресурсов jar'а — с той же политикой, что у нативной сборки (`WebRoutes.kt`):
 * `immutable` только по имени-хешу, всё прочее перепроверяется по ETag, тело сжимается.
 *
 * Разница в том, где сжимается: здесь `Compression` делает это на лету, нативная сборка отдаёт
 * файлы, сжатые при сборке образа. Brotli здесь нет — у `ktor-server-compression` его кодека нет.
 *
 * Оба плагина ставятся на маршрут статики, а не на приложение: API нативной сборки не сжимается,
 * и JVM-сборке незачем отвечать на те же запросы иначе.
 */
fun Routing.wasmJsApp() {
    staticResources("/", "static", index = "index.html") {
        contentType {
            if (it.path.endsWith(".wasm")) {
                ContentType("application", "wasm")
            } else {
                null
            }
        }

        default("index.html")

        // Без валидатора `no-cache` значил бы полную перекачку на каждый заход.
        etag(ETagProvider.StrongSha256)
        modify { resource, call ->
            call.response.header(HttpHeaders.CacheControl, cacheControlFor(resource.path))
            call.response.header(HttpHeaders.Vary, HttpHeaders.AcceptEncoding)
        }
    }.apply {
        install(ConditionalHeaders)
        install(Compression) { gzip() }
    }
}
