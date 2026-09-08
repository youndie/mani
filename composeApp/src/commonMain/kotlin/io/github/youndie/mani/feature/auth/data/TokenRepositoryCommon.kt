package io.github.youndie.mani.feature.auth.data

import io.ktor.client.plugins.auth.providers.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class TokenRepositoryCommon(private val storage: TokenStorage) : TokenRepository {
    private val token: MutableStateFlow<BearerTokens> = MutableStateFlow(
        storage.load() ?: BearerTokens("", ""),
    )

    /**
     * Без повтора (`replay = 0`): истёкшая сессия — новость одного момента. С повтором она
     * доставалась бы каждому новому подписчику, то есть выкидывала бы человека на витрину при
     * следующей же пересборке экрана.
     */
    private val expiredEvents = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val expired: Flow<Unit> = expiredEvents.asSharedFlow()

    override fun getToken(): BearerTokens = token.value

    override fun set(accessToken: String, refreshToken: String) {
        this.token.value = BearerTokens(accessToken, refreshToken)

        storage.save(this.token.value)
    }

    override fun expire() {
        set("", "")
        expiredEvents.tryEmit(Unit)
    }

    override fun observeToken(): StateFlow<BearerTokens> = token.asStateFlow()
}
