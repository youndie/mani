package io.github.youndie.mani.feature.categories.data

import io.github.youndie.mani.feature.transaction.BaseFlowRepository
import io.github.youndie.mani.feature.transaction.Category
import io.github.youndie.mani.feature.transaction.DataSource

class CategoriesRepository(dataSource: DataSource<Category>) : BaseFlowRepository<Category>(dataSource)
