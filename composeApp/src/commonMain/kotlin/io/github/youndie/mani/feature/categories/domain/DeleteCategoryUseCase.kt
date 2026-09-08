package io.github.youndie.mani.feature.categories.domain

import io.github.youndie.mani.feature.categories.data.CategoriesRepository
import io.github.youndie.mani.feature.transaction.Category
import io.github.youndie.mani.feature.transaction.domain.TransactionRepository
import io.github.youndie.mani.useCase.UseCase
import io.github.youndie.mani.utilz.suspendRunCatching

class DeleteCategoryUseCase(
    private val repository: CategoriesRepository,
    private val transactionRepository: TransactionRepository,
) : UseCase<Category, Boolean>() {
    /**
     * Перечитывание правил — часть удаления, а не шаг рядом с ним: у снятой категории остаются
     * правила, и они должны стать «Default», а не показывать имя, которого больше нет.
     *
     * Оно стоит ВНУТРИ `suspendRunCatching`, и это и есть правка. Снаружи, в `also`, отказ сети
     * летел мимо `Result` прямо в `viewModelScope.launch` — то есть в падение приложения, а не
     * в сообщение на экране. Заодно перечитывание больше не происходит после неудачного
     * удаления: перечитывать нечего, категория на месте.
     */
    override suspend fun invoke(params: Category): Result<Boolean> = suspendRunCatching {
        val deleted = repository.delete(params.id)

        transactionRepository.load()

        deleted
    }
}
