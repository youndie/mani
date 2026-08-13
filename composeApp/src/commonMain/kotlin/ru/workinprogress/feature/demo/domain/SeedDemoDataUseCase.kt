package ru.workinprogress.feature.demo.domain

import io.ktor.client.HttpClient
import io.ktor.client.plugins.resources.post
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.workinprogress.feature.demo.DemoResource
import ru.workinprogress.feature.transaction.domain.TransactionRepository
import ru.workinprogress.mani.data.ServerException
import ru.workinprogress.useCase.EmptyParams

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
    override suspend operator fun invoke(params: EmptyParams): Result<Boolean> =
        try {
            withContext(Dispatchers.Default) {
                val response = httpClient.post(DemoResource.Seed())

                if (response.status != HttpStatusCode.Created) {
                    Result.Error(ServerException("Couldn't fill the demo data"))
                } else {
                    transactionRepository.load()
                    Result.Success(true)
                }
            }
        } catch (e: Exception) {
            Result.Error(ServerException(message = "Couldn't fill the demo data", cause = e))
        }
}
