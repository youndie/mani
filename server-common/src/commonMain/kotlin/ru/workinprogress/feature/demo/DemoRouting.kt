package ru.workinprogress.feature.demo

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import org.koin.ktor.ext.inject
import ru.workinprogress.feature.demo.data.DemoService
import ru.workinprogress.feature.user.currentUserId
import ru.workinprogress.mani.config.JWTConfig

fun Routing.demoRouting() {
    val demoService by inject<DemoService>()
    val jwtConfig by inject<JWTConfig>()

    post<DemoResource> {
        val tokens = demoService.createSandbox()
        if (tokens == null) {
            call.respond(HttpStatusCode.InternalServerError)
            return@post
        }
        call.respond(HttpStatusCode.Created, tokens)
    }

    // Засев своего аккаунта — операция владельца, поэтому под проверкой токена, в отличие от
    // создания песочницы, которое как раз и выдаёт первый токен.
    authenticate(jwtConfig.name) {
        post<DemoResource.Seed> {
            demoService.seed(call.currentUserId())
            call.respond(HttpStatusCode.Created)
        }
    }
}
