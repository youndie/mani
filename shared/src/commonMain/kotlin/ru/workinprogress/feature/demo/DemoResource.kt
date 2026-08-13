package ru.workinprogress.feature.demo

import io.ktor.resources.Resource

/**
 * Песочница: `POST /demo` заводит одноразового пользователя с готовыми данными и сразу отдаёт
 * [ru.workinprogress.feature.auth.Tokens].
 *
 * Тела у запроса нет — посетитель ничего не вводит, в этом и смысл: «no account, no password».
 */
@Resource("/demo")
class DemoResource
