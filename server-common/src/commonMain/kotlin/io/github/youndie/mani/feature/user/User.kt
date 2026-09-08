package io.github.youndie.mani.feature.user

import io.github.youndie.mani.security.ManiPrincipal
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import kotlinx.serialization.Serializable

@Serializable
data class User(val id: String = "", val username: String = "unknown")

/**
 * Идентификатор того, кто пришёл.
 *
 * Зовётся **только внутри `authenticate`**, а туда запрос без принципала не доходит: 401 отдаёт
 * сам провайдер. Отсутствие принципала здесь — не состояние запроса, а незащищённый маршрут, то
 * есть ошибка проводки, и она обязана быть громкой.
 *
 * Прежняя версия на этот случай отвечала 401 и возвращала ПУСТУЮ СТРОКУ, не прерывая обработчик.
 * Внутри `authenticate` ветка мертва, но за его пределами пустая строка ушла бы в хранилище как
 * владелец, а следующий `respond` поверх уже отправленного 401 — в исключение. Ловушка ждала
 * первого маршрута, забывшего `authenticate`.
 */
fun ApplicationCall.currentUserId(): String = principal<ManiPrincipal>()?.id
    ?: error("currentUserId() вызван вне authenticate: маршрут не защищён проверкой токена")
