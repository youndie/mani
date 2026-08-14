package ru.workinprogress.feature.health

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

/** `GET /health` — чем именно отвечает сервер. Без авторизации: это витрина, а не данные. */
@Resource("/health")
class HealthResource

/**
 * @param build какая сборка отвечает — `jvm` или `kotlin/native`
 * @param version версия приложения
 * @param uptimeSeconds сколько эта сборка работает
 */
@Serializable
data class Health(val build: String, val version: String, val uptimeSeconds: Long)
