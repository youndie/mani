package io.github.youndie.mani.feature.demo.domain

import io.github.youndie.mani.data.ServerException
import io.github.youndie.mani.feature.demo.DemoResource
import io.github.youndie.mani.feature.transaction.domain.TransactionRepository
import io.github.youndie.mani.useCase.EmptyParams
import io.github.youndie.mani.utilz.suspendRunCatching
import io.ktor.client.HttpClient
import io.ktor.client.plugins.resources.post
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Заполняет **текущий** аккаунт данными сида — тем же, что видит гость песочницы.
 *
 * После засева список перечитывается: записи завёл сервер, и в клиентском хранилище о них
 * ничего нет. Без этого экран остался бы пустым, хотя данные уже созданы.
 */
class SeedDemoDataUseCase(
    private val httpClient: HttpClient,
    private val transactionRepository: TransactionRepository,
) : SeedUseCase() {
    override suspend operator fun invoke(params: EmptyParams): Result<Boolean> = suspendRunCatching {
        withContext(Dispatchers.Default) {
            val response = httpClient.post(DemoResource.Seed())

            if (response.status != HttpStatusCode.Created) {
                Result.failure(ServerException("Couldn't fill the demo data"))
            } else {
                transactionRepository.load()
                Result.success(true)
            }
        }
    }.getOrElse { Result.failure(ServerException(message = "Couldn't fill the demo data", cause = it)) }
}
