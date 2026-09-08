package io.github.youndie.mani.config

import dev.whyoleg.cryptography.random.CryptographyRandom
import io.github.youndie.mani.security.toHex
import io.ktor.http.encodeURLParameter

/**
 * Конфигурация сервера — из переменных окружения, одинаково для обеих сборок.
 *
 * HOCON (`application.conf`) читает JVM-only код Ktor, поэтому нативной сборке он недоступен.
 * Замена на ENV не только вынужденная: имена переменных здесь те же, что уже стояли в
 * `application.conf` как `${?...}`-подстановки и в `.k8s-templates/deployment.yaml`, так что
 * конфигурация двух сборок **сходится**, а не расходится.
 */
data class ManiConfig(
    val port: Int,
    val mongo: MongoConfig,
    val jwt: JWTConfig,
    /**
     * Каталог со собранным wasm-приложением. Пусто — статика не отдаётся вовсе (так удобно
     * гонять сервер в тестах и локально, без сборки фронтенда).
     */
    val webRoot: String?,
    val development: Boolean,
) {
    companion object {
        fun fromEnv(): ManiConfig = ManiConfig(
            port = readEnv("PORT")?.toIntOrNull() ?: 8080,
            mongo =
            MongoConfig(
                userName = readEnv("MONGO_USERNAME").orEmpty(),
                password = readEnv("MONGO_PASSWORD").orEmpty(),
                host = readEnv("MONGO_HOST") ?: "localhost",
                database = readEnv("MONGO_DATABASE") ?: "mani",
            ),
            jwt =
            JWTConfig(
                name = readEnv("JWT_NAME") ?: "auth-jwt",
                realm = readEnv("JWT_REALM") ?: "mani",
                secret = signingSecret(readEnv("JWT_SECRET")),
                audience = readEnv("JWT_AUDIENCE") ?: "jwt-audience",
                issuer = readEnv("JWT_ISSUER") ?: "jwt-issuer",
                expirationSeconds = readEnv("JWT_EXPIRATION_SECONDS")?.toLongOrNull() ?: 3600L,
            ),
            webRoot = readEnv("MANI_WEB_ROOT"),
            development = readEnv("MANI_DEVELOPMENT")?.toBooleanStrictOrNull() ?: false,
        )
    }
}

/**
 * Секрет подписи токенов: из окружения, а если его там нет — случайный на этот процесс.
 *
 * Прежнее умолчание было строкой `secret`, и это не теоретическая беда: переменную задаёт только
 * `.k8s-templates/deployment.yaml`, а `docker-compose.yaml` из README — нет. Любой, кто поднял
 * стенд по инструкции, подписывал токены значением, напечатанным в исходниках, то есть подделать
 * их мог кто угодно.
 *
 * Падать без переменной было бы честнее всего, но это сломало бы `docker compose up` из README
 * ради выгоды, которой у локального стенда нет. Случайный секрет оставляет стенд рабочим, а
 * плату делает видимой: перезапуск процесса разлогинивает всех, и строка в логе говорит почему.
 *
 * Отдельной функцией, а не `?:` внутри [ManiConfig.fromEnv]: решение принимается по значению
 * переменной, и проверять его надо по значению, а не по окружению машины, на которой идут тесты.
 */
internal fun signingSecret(fromEnvironment: String?): String {
    if (fromEnvironment != null) return fromEnvironment

    // `println`, а не логгер: его в общей части нет ни у одной сборки, а в контейнере stdout
    // и есть лог.
    println(
        "mani: JWT_SECRET не задан — токены подписываются случайным секретом этого процесса. " +
            "Перезапуск разлогинит всех; для стенда задайте переменную.",
    )
    return CryptographyRandom.nextBytes(SECRET_BYTES).toHex()
}

private const val SECRET_BYTES = 32

data class MongoConfig(
    val userName: String = "",
    val password: String = "",
    val host: String = "",
    /** Отдельным полем, а не константой: тесты работают в своей базе, а не в базе стенда. */
    val database: String = "mani",
) {
    /**
     * Строка подключения.
     *
     * Логин и пароль ПОДСТАВЛЯЮТСЯ, если заданы. Раньше поля читались из окружения и никуда не
     * попадали: тот, кто задавал `MONGO_PASSWORD`, получал подключение без пароля и узнавал об
     * этом не из отказа, а из того, что всё почему-то работает. Умолчание пустое — mongod стенда
     * поднят без аутентификации, и с пустыми полями строка та же, что была.
     *
     * Значения экранируются: пароль с `@` или `:` иначе рвёт разбор адреса, и подключение уходит
     * не туда, куда просили.
     */
    val connectionString: String get() = when {
        userName.isEmpty() -> "mongodb://$host/?w=majority&appName=Mani"

        else -> "mongodb://${userName.encodeURLParameter()}:${password.encodeURLParameter()}@" +
            "$host/?w=majority&appName=Mani"
    }
}

data class JWTConfig(
    val name: String = "auth-jwt",
    val realm: String = "mani",
    val secret: String = "secret",
    val audience: String = "jwt-audience",
    val issuer: String = "jwt-issuer",
    val expirationSeconds: Long = 3600L,
)

/**
 * В `commonMain` нет `java.*`, а `System.getenv` есть только на JVM — отсюда expect/actual.
 */
expect fun readEnv(name: String): String?
