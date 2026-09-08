package io.github.youndie.mani.security

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import io.github.youndie.mani.config.JWTConfig
import io.github.youndie.mani.utilz.suspendRunCatching
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.long
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Что сервер кладёт в токен и что достаёт из него обратно. */
data class TokenClaims(val id: String, val username: String)

/**
 * Для чего выдан токен.
 *
 * Пара живёт по разным правилам: access ходит в каждом запросе и потому короткий, refresh лежит
 * в базе и живёт месяц. Пока вид не был записан В САМ токен, проверка не могла их различить, и
 * refresh принимался везде, где ждали access, — то есть срок access-токена не значил ничего.
 */
enum class TokenKind(val claim: String) {
    Access("access"),
    Refresh("refresh"),
}

/**
 * Выдача и проверка JWT — **одним кодом на обеих сборках**.
 *
 * Раньше этим занимался `com.auth0:java-jwt`, которого под Kotlin/Native нет. Заманчивая
 * альтернатива — expect/actual с двумя реализациями — здесь опаснее отсутствия нативной сборки
 * вовсе: подпись, набор claim'ов и правила проверки разъехались бы молча, и увидели бы мы это
 * не на сборке, а в тот день, когда токен, выданный одним образом, не принял другой.
 *
 * Поэтому реализация общая, поверх `cryptography-kotlin` (JDK-провайдер на JVM, OpenSSL на
 * native), а совместимость с прежним форматом проверяется тестом: токен, подписанный
 * `java-jwt`, обязан проходить эту проверку — в базе лежат выданные им refresh-токены.
 */
@OptIn(ExperimentalTime::class)
class TokenService(private val config: JWTConfig) {
    private val hmac = CryptographyProvider.Default.get(HMAC)

    private val key by lazy {
        hmac.keyDecoder(SHA256).decodeFromByteArrayBlocking(
            HMAC.Key.Format.RAW,
            config.secret.encodeToByteArray(),
        )
    }

    /**
     * @param kind для чего токен выдаётся; пишется в claim `kind` и сверяется при проверке
     * @param expiration момент истечения; по умолчанию — `expirationSeconds` от текущего времени
     */
    @Suppress(
        "ktlint:kapkan:wall-clock",
        "выпуск берёт срок параметром, проверка сверяет им же выданный токен",
    )
    suspend fun issue(
        id: String,
        username: String,
        kind: TokenKind,
        expiration: Instant = Clock.System.now().plus(config.expirationSeconds.seconds),
    ): String {
        val header =
            buildJsonObject {
                put("typ", JsonPrimitive("JWT"))
                put("alg", JsonPrimitive(ALGORITHM))
            }
        val payload =
            buildJsonObject {
                put("sub", JsonPrimitive(SUBJECT))
                put("aud", JsonPrimitive(config.audience))
                put("iss", JsonPrimitive(config.issuer))
                put("id", JsonPrimitive(id))
                put("username", JsonPrimitive(username))
                put("exp", JsonPrimitive(expiration.epochSeconds))
                // Без `jti` два токена одного пользователя, выданных в одну секунду, совпадают
                // байт в байт: `exp` в JWT хранится в секундах, а остальные claim'ы одинаковы.
                // На обновлении это значит, что новый refresh-токен равен старому — то есть
                // ротации нет, и украденный токен остаётся действительным. Поймано тестом
                // `refresh returns a new pair and burns the old token`, до него это выглядело
                // как исправная работа.
                put("jti", JsonPrimitive(CryptographyRandom.nextBytes(JTI_BYTES).encodeBase64Url()))
                put("kind", JsonPrimitive(kind.claim))
            }

        val signingInput =
            Json.encodeToString(JsonObject.serializer(), header).encodeToByteArray().encodeBase64Url() +
                "." +
                Json.encodeToString(JsonObject.serializer(), payload).encodeToByteArray().encodeBase64Url()

        val signature = key.signatureGenerator().generateSignature(signingInput.encodeToByteArray())
        return signingInput + "." + signature.encodeBase64Url()
    }

    /**
     * @param expect каким токен обязан быть. Проверка вида — не формальность: refresh живёт
     *   месяц, и принимать его там, где ждут access, значит выдавать месячный пропуск вместо
     *   часового.
     * @return claims, если подпись сошлась, вид совпал и токен не просрочен; иначе `null`.
     *   Никаких исключений наружу: для вызывающего «подпись не сошлась» и «токен испорчен» —
     *   один и тот же ответ, 401. Исключение — отмена: отменённый запрос не должен
     *   превращаться в 401, поэтому проверка идёт через `suspendRunCatching`.
     */
    @Suppress(
        "ktlint:kapkan:wall-clock",
        "сервер сверяет срок им же выданного токена со своими часами — это и есть проверка exp",
    )
    suspend fun verify(token: String, expect: TokenKind): TokenClaims? {
        val parts = token.split('.')
        if (parts.size != 3) return null

        return suspendRunCatching {
            val header = Json.parseToJsonElement(parts[0].decodeBase64Url().decodeToString()) as JsonObject
            if ((header["alg"] as? JsonPrimitive)?.contentOrNull != ALGORITHM) return null

            // Проверка через `verify` провайдера, а не сравнением массивов: побайтовое сравнение
            // «пока не разойдётся» утекает время и превращается в подбор подписи.
            val valid =
                key.signatureVerifier().tryVerifySignature(
                    "${parts[0]}.${parts[1]}".encodeToByteArray(),
                    parts[2].decodeBase64Url(),
                )
            if (!valid) return null

            val payload = Json.parseToJsonElement(parts[1].decodeBase64Url().decodeToString()) as JsonObject

            if (config.issuer !in payload.stringOrArray("iss")) return null
            if (config.audience !in payload.stringOrArray("aud")) return null

            // Токены, выданные до появления claim'а, приходят без него. Как access их принимать
            // нельзя — ради этого всё и делается. Как refresh — можно и нужно: они лежат в базе
            // стенда, и отказ разлогинил бы каждого, кто зашёл до выката. Подлога этим не
            // открывается: `/auth/refresh` сверяет предъявленный токен ещё и с базой, а
            // access-токены туда не попадают. Через месяц последний такой токен истечёт сам.
            val kind = (payload["kind"] as? JsonPrimitive)?.contentOrNull
            if (kind != expect.claim && !(kind == null && expect == TokenKind.Refresh)) return null

            val exp = (payload["exp"] as? JsonPrimitive)?.long ?: return null
            if (exp <= Clock.System.now().epochSeconds) return null

            val id = (payload["id"] as? JsonPrimitive)?.contentOrNull ?: return null
            val username = (payload["username"] as? JsonPrimitive)?.contentOrNull.orEmpty()

            TokenClaims(id = id, username = username)
        }.getOrNull()
    }

    /**
     * `aud` и `iss` по RFC 7519 бывают и строкой, и массивом строк; `java-jwt` пишет строку для
     * одного значения и массив для нескольких. Разбирать надо оба вида, иначе однажды
     * добавленная вторая аудитория тихо перестанет проходить проверку.
     */
    private fun JsonObject.stringOrArray(key: String): List<String> = when (val value = this[key]) {
        is JsonPrimitive -> listOfNotNull(value.contentOrNull)
        is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        else -> emptyList()
    }

    private companion object {
        const val ALGORITHM = "HS256"
        const val JTI_BYTES = 16

        /** Так подписывал `java-jwt`; менять нельзя — старые токены проверяются этим же кодом. */
        const val SUBJECT = "Authentication"
    }
}
