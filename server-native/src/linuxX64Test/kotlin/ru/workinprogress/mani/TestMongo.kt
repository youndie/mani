package ru.workinprogress.mani

import ru.workinprogress.mani.config.JWTConfig
import ru.workinprogress.mani.config.ManiConfig
import ru.workinprogress.mani.config.MongoConfig
import ru.workinprogress.mani.config.readEnv
import ru.workinprogress.mongkn.MongoClient
import ru.workinprogress.mongkn.bson.BsonObjectId

/**
 * Настоящий mongod, а не подделка.
 *
 * Здесь всё, что могло сломаться, ломается **тихо**: фильтр по `_id` строкой ничего не находит,
 * `amount` строкой пишется без ошибки, отсутствие `@SerialName("_id")` заводит документу
 * посторонний ключ. Юнит-тест с подставным хранилищем не увидит ни одного из этих случаев —
 * их видит только сервер.
 *
 * Адрес берётся из `MANI_TEST_MONGO_HOST`; по умолчанию — локальный, как его поднимает
 * `docker compose`. Каждый прогон работает в **своей** базе, поэтому тесты не мешают друг другу
 * и не трогают базу стенда.
 */
object TestMongo {
    val host: String get() = readEnv("MANI_TEST_MONGO_HOST") ?: "127.0.0.1:27017"

    fun uniqueDatabaseName(prefix: String): String = "mani_test_${prefix}_${BsonObjectId.generate().hex}"

    fun client(): MongoClient = MongoClient("mongodb://$host/?w=majority&appName=ManiTest")

    fun config(webRoot: String? = null): ManiConfig = ManiConfig(
        port = 0,
        mongo = MongoConfig(host = host),
        jwt =
        JWTConfig(
            secret = "test-secret",
            audience = "jwt-audience",
            issuer = "jwt-issuer",
            expirationSeconds = 3600,
        ),
        webRoot = webRoot,
        development = false,
    )
}
