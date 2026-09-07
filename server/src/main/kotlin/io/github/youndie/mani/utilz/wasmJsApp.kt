package io.github.youndie.mani.utilz

import io.ktor.http.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*

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
