package ru.workinprogress.feature.transaction

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
import ru.workinprogress.feature.category.CategoryRepository
import ru.workinprogress.feature.transaction.data.TransactionRepository
import ru.workinprogress.feature.transaction.data.toTransaction
import ru.workinprogress.feature.user.currentUserId
import ru.workinprogress.mani.config.JWTConfig

fun Routing.transactionRouting() {
    val transactionRepository by inject<TransactionRepository>()
    val categoryRepository by inject<CategoryRepository>()
    val jwtConfig by inject<JWTConfig>()

    authenticate(jwtConfig.name) {
        post<TransactionResource> {
            val transaction = call.receive<Transaction>()
            val userId = call.currentUserId()

            // Категории читаются здесь, а не в репозитории: они лежат в документе пользователя,
            // и репозиторий транзакций о них не знает. Забыть эту подстановку — значит отдать
            // клиенту все транзакции с категорией по умолчанию, ничего при этом не сломав.
            val categories = categoryRepository.getByUser(userId)

            val id = transactionRepository.create(transaction, userId)
            val added = transactionRepository.getById(id)
            if (added == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            call.respond(HttpStatusCode.Created, added.toTransaction(categories))
        }

        get<TransactionResource> {
            val userId = call.currentUserId()
            val categories = categoryRepository.getByUser(userId)

            call.respond(
                HttpStatusCode.OK,
                transactionRepository.getByUser(userId).map { it.toTransaction(categories) },
            )
        }

        patch<TransactionResource.ById> { path ->
            val new = call.receive<Transaction>()
            val old = transactionRepository.getById(path.id)

            if (old?.userId != call.currentUserId()) {
                call.respond(HttpStatusCode.Forbidden)
                return@patch
            }
            transactionRepository.update(new, old.userId)
            call.respond(HttpStatusCode.OK, new)
        }

        delete<TransactionResource.ById> { path ->
            val transaction = transactionRepository.getById(path.id)

            if (transaction?.userId != call.currentUserId()) {
                call.respond(HttpStatusCode.Forbidden)
                return@delete
            }

            transactionRepository.delete(path.id)
            call.respond(HttpStatusCode.OK)
        }
    }
}
