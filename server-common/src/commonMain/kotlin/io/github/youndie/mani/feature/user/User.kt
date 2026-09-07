package io.github.youndie.mani.feature.user

import io.github.youndie.mani.security.ManiPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable

@Serializable
data class User(val id: String = "", val username: String = "unknown")

suspend fun ApplicationCall.currentUserId(): String = principal<ManiPrincipal>()?.id ?: run {
    respond(HttpStatusCode.Unauthorized)
    ""
}
