package ru.workinprogress.feature.demo

import io.ktor.resources.Resource

/**
 * Песочница: `POST /demo` заводит одноразового пользователя с готовыми данными и сразу отдаёт
 * [ru.workinprogress.feature.auth.Tokens].
 *
 * Тела у запроса нет — посетитель ничего не вводит, в этом и смысл: «no account, no password».
 */
@Resource("/demo")
class DemoResource {
    /**
     * `POST /demo/seed` — засеять теми же данными **текущего** пользователя.
     *
     * Нужен пустому экрану: у человека уже есть аккаунт, заводить ему второй незачем, а
     * посмотреть, как выглядит заполненное приложение, хочется.
     */
    @Resource("seed")
    class Seed(val parent: DemoResource = DemoResource())
}
