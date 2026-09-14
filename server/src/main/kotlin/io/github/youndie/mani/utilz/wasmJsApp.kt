package io.github.youndie.mani.utilz

import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.cacheControl
import io.ktor.http.contentType
import io.ktor.server.http.content.default
import io.ktor.server.http.content.file
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.Routing
import io.ktor.server.routing.contentType

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
        cacheControl { file ->
            if (file.file.contains("ttf")) {
                listOf(Immutable, CacheControl.MaxAge(10000))
            } else {
                emptyList()
            }
        }
    }
}

object Immutable : CacheControl(null) {
    override fun toString(): String = "immutable"
}
