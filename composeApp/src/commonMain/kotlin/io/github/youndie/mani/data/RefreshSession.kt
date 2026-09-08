package io.github.youndie.mani.data

import io.github.youndie.mani.feature.auth.AuthResource
import io.github.youndie.mani.feature.auth.RefreshParams
import io.github.youndie.mani.feature.auth.Tokens
import io.github.youndie.mani.feature.auth.data.TokenRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

/**
 * Обменивает refresh-токен на новую пару.
 *
 * Отдельной функцией, а не телом `refreshTokens { }` внутри настройки клиента: там она
 * проверяется только живым сервером, а здесь — подставным движком, то есть тем же способом, каким
 * проверяется всё остальное в этом слое.
 *
 * @param markAsRefreshRequest пометка «это и есть запрос обновления». Она приходит параметром,
 *   потому что `markAsRefreshTokenRequest()` — метод области `refreshTokens { }` у Ktor, и вне
 *   этой области его не существует. Без пометки плагин попытался бы обновить токен для самого
 *   запроса обновления, то есть закольцевался бы.
 * @return новая пара, либо `null`, если сессию продлить не удалось
 */
internal suspend fun refreshSession(
    httpClient: HttpClient,
    tokenRepository: TokenRepository,
    markAsRefreshRequest: HttpRequestBuilder.() -> Unit = {},
): BearerTokens? {
    val refreshToken = tokenRepository.getToken().refreshToken
    if (refreshToken.isNullOrEmpty()) return null

    val response = httpClient.post(AuthResource.Refresh()) {
        markAsRefreshRequest()
        setBody(RefreshParams(refreshToken))
    }

    // Сервер отверг токен — сессия кончилась, и это надо СКАЗАТЬ, а не только записать.
    //
    // Раньше здесь токены сбрасывались, а следом всё равно читалось тело — которого у 401 нет.
    // Разбор падал, и наружу выходило не «сессия истекла», а отказ сети: экран «сервер
    // недоступен» с обратным отсчётом и тремя повторами, из которого нет пути на витрину.
    if (response.status == HttpStatusCode.Unauthorized) {
        tokenRepository.expire()
        return null
    }

    // Прочий отказ — не повод разлогинивать: 500 у сервера сессии не отменяет. Ktor на `null`
    // прекратит попытки, и исходный запрос вернётся с 401, а экран покажет отказ.
    if (!response.status.isSuccess()) return null

    val tokens = response.body<Tokens>()
    tokenRepository.set(accessToken = tokens.accessToken, refreshToken = tokens.refreshToken)

    return tokenRepository.getToken()
}
