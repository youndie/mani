package ru.workinprogress.feature.demo

import io.ktor.http.HttpStatusCode
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import org.koin.ktor.ext.inject
import ru.workinprogress.feature.demo.data.DemoService

fun Routing.demoRouting() {
    val demoService by inject<DemoService>()

    post<DemoResource> {
        val tokens = demoService.createSandbox()
        if (tokens == null) {
            call.respond(HttpStatusCode.InternalServerError)
            return@post
        }
        call.respond(HttpStatusCode.Created, tokens)
    }
}
