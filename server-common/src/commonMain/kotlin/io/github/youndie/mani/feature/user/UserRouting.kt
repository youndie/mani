package io.github.youndie.mani.feature.user

import io.github.youndie.mani.feature.auth.LoginParams
import io.github.youndie.mani.feature.user.data.UserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import org.koin.ktor.ext.inject

fun Routing.userRouting() {
    val userRepository by inject<UserRepository>()

    post<UserResource> {
        val params = call.receive<LoginParams>()
        if (userRepository.findByUsername(params.name) != null) {
            call.respond(HttpStatusCode.BadRequest, "User already exist")
            return@post
        }
        // Прежняя версия при неудачной записи не отвечала вовсе, и клиент ждал таймаута.
        // Отказ хранилища — это 500, а не тишина.
        if (userRepository.save(params) == null) {
            call.respond(HttpStatusCode.InternalServerError)
            return@post
        }
        call.respond(HttpStatusCode.Created)
    }
}
