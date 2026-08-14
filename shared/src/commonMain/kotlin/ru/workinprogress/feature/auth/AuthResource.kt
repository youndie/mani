package ru.workinprogress.feature.auth

import io.ktor.resources.Resource

@Resource("/auth")
class AuthResource {

    @Resource("refresh")
    class Refresh(val parent: AuthResource = AuthResource())
}
