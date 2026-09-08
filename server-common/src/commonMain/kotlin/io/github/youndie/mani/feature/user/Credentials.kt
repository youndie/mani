package io.github.youndie.mani.feature.user

import io.github.youndie.mani.feature.auth.LoginParams
import io.github.youndie.mani.feature.demo.data.DEMO_USERNAME_PREFIX

private val NAME_LENGTH = 3..32
private const val MIN_PASSWORD_LENGTH = 8

/**
 * Чем плохи эти учётные данные, либо `null`, если ничем.
 *
 * Проверяется **только регистрация**. Вход обязан принимать имя, каким бы оно ни было: у тех, кто
 * завёлся до этих правил, имена им не подчиняются, и запрет на входе означал бы, что правило
 * выселило существующих пользователей.
 *
 * Текст возвращается наружу как есть — его показывает форма. Поэтому он на английском, как и
 * остальные надписи приложения, и говорит, что сделать, а не что нарушено.
 */
fun credentialsProblem(params: LoginParams): String? = when {
    params.name.length !in NAME_LENGTH ->
        "Name must be ${NAME_LENGTH.first} to ${NAME_LENGTH.last} characters long"

    params.name.any { it !in 'a'..'z' && it !in '0'..'9' && it != '-' && it != '_' } ->
        "Name may contain lowercase letters, digits, hyphen and underscore only"

    // Имя песочницы выдаёт сервер, и по этому префиксу уборка отличает её от настоящего
    // пользователя. Заняв его руками, можно было завести аккаунт, который через сутки унесёт
    // уборщик, — вместе с данными, которых он не ждал.
    params.name.startsWith(DEMO_USERNAME_PREFIX) ->
        "Names starting with \"$DEMO_USERNAME_PREFIX\" are reserved for the demo"

    params.password.length < MIN_PASSWORD_LENGTH ->
        "Password must be at least $MIN_PASSWORD_LENGTH characters long"

    else -> null
}
