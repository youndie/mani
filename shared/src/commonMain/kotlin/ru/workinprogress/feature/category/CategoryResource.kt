package ru.workinprogress.feature.category

import io.ktor.resources.*

@Resource("/categories")
class CategoryResource {

    @Resource("/{id}")
    class ById(val parent: CategoryResource = CategoryResource(), val id: String)
}
