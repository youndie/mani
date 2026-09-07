package io.github.youndie.mani.feature.category

import io.github.youndie.mani.config.JWTConfig
import io.github.youndie.mani.feature.transaction.Category
import io.github.youndie.mani.feature.user.currentUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.patch
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import org.koin.ktor.ext.inject

fun Routing.categoryRouting() {
    val jwtConfig by inject<JWTConfig>()
    val categoryRepository by inject<CategoryRepository>()

    authenticate(jwtConfig.name) {
        get<CategoryResource> {
            call.respond(categoryRepository.getByUser(call.currentUserId()))
        }

        post<CategoryResource> {
            call.respond(categoryRepository.create(call.receive<Category>(), call.currentUserId()))
        }

        get<CategoryResource.ById> { path ->
            if (categoryRepository.getByUser(call.currentUserId()).none { it.id == path.id }) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }

            val category = categoryRepository.getById(path.id)
            if (category == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            call.respond(category)
        }

        patch<CategoryResource.ById> { path ->
            if (categoryRepository.getByUser(call.currentUserId()).none { it.id == path.id }) {
                call.respond(HttpStatusCode.Forbidden)
                return@patch
            }

            call.respond(categoryRepository.update(call.receive<Category>()))
        }

        delete<CategoryResource.ById> { path ->
            if (categoryRepository.getByUser(call.currentUserId()).none { it.id == path.id }) {
                call.respond(HttpStatusCode.Forbidden)
                return@delete
            }

            categoryRepository.delete(path.id)
            call.respond(HttpStatusCode.OK)
        }
    }
}
