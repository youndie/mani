package io.github.youndie.mani.feature.user

import io.ktor.resources.Resource

@Resource("/users")
class UserResource {

    @Resource("/current")
    class CurrentUserResource(val parent: UserResource)
}
