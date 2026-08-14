package ru.workinprogress.mani.config

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
                userName = readEnv("MONGO_USERNAME") ?: "root",
                password = readEnv("MONGO_PASSWORD") ?: "example",
                host = readEnv("MONGO_HOST") ?: "localhost",
                database = readEnv("MONGO_DATABASE") ?: "mani",
            ),
            jwt =
            JWTConfig(
                name = readEnv("JWT_NAME") ?: "auth-jwt",
                realm = readEnv("JWT_REALM") ?: "mani",
                secret = readEnv("JWT_SECRET") ?: "secret",
                audience = readEnv("JWT_AUDIENCE") ?: "jwt-audience",
                issuer = readEnv("JWT_ISSUER") ?: "jwt-issuer",
                expirationSeconds = readEnv("JWT_EXPIRATION_SECONDS")?.toLongOrNull() ?: 3600L,
            ),
            webRoot = readEnv("MANI_WEB_ROOT"),
            development = readEnv("MANI_DEVELOPMENT")?.toBooleanStrictOrNull() ?: false,
        )
    }
}

data class MongoConfig(
    val userName: String = "",
    val password: String = "",
    val host: String = "",
    /** Отдельным полем, а не константой: тесты работают в своей базе, а не в базе стенда. */
    val database: String = "mani",
) {
    /**
     * Строка подключения. Логин и пароль в неё намеренно не подставляются: mongod стенда поднят
     * без аутентификации, и так же собирала её JVM-сборка. Поля оставлены, чтобы включение
     * аутентификации было правкой одного места, а не поиском по репозиторию.
     */
    val connectionString: String get() = "mongodb://$host/?w=majority&appName=Mani"
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
