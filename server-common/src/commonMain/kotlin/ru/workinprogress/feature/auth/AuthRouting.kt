package ru.workinprogress.feature.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import org.koin.ktor.ext.inject
import ru.workinprogress.feature.auth.data.AuthService

fun Routing.authRouting() {
    val authService by inject<AuthService>()

    post<AuthResource> {
        val credentials = call.receive<LoginParams>()
        val response = authService.authenticate(credentials)
        if (response == null) {
            call.respond(HttpStatusCode.NotFound)
            return@post
        }
        call.respond(response)
    }

    post<AuthResource.Refresh> {
        val request = call.receive<RefreshParams>()
        val tokens = authService.refreshToken(refreshToken = request.refreshToken)
        if (tokens == null) {
            call.respond(HttpStatusCode.Unauthorized)
            return@post
        }
        call.respond(tokens)
    }
}
