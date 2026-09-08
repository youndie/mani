package io.github.youndie.mani.feature.health

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

/**
 * `GET /health` — чем именно отвечает сервер. Без авторизации: это витрина, а не данные.
 *
 * Зависимостей не трогает намеренно: на него смотрит проба живости, а живость не должна зависеть
 * от базы. Иначе падение базы перезапускает все поды разом — и чинит этим ровно ничего.
 */
@Resource("/health")
class HealthResource {
    /**
     * `GET /health/ready` — готов ли сервер обслуживать: хранилище отвечает.
     *
     * Отдельно от `/health`, потому что вопросы разные. «Процесс жив» и «процессу есть с чем
     * работать» — не одно и то же, и путать их дорого: под без базы должен перестать получать
     * трафик, а не уйти в перезапуск.
     */
    @Resource("ready")
    class Ready(val parent: HealthResource = HealthResource())
}

/**
 * @param build какая сборка отвечает — `jvm` или `kotlin/native`
 * @param version версия приложения
 * @param uptimeSeconds сколько эта сборка работает
 */
@Serializable
data class Health(val build: String, val version: String, val uptimeSeconds: Long)
