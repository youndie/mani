package ru.workinprogress.feature.categories.data

import ru.workinprogress.feature.transaction.BaseFlowRepository
import ru.workinprogress.feature.transaction.Category
import ru.workinprogress.feature.transaction.DataSource

class CategoriesRepository(dataSource: DataSource<Category>) : BaseFlowRepository<Category>(dataSource)
