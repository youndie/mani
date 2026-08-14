package ru.workinprogress.feature.transaction.ui.component.model

import kotlinx.datetime.LocalDate
import ru.workinprogress.feature.transaction.Category
import ru.workinprogress.feature.transaction.Transaction

sealed class TransactionAction {
    data class AmountChanged(val amount: String) : TransactionAction()
    data class CommentChanged(val comment: String) : TransactionAction()
    data class IncomeChanged(val income: Boolean) : TransactionAction()
    data class PeriodChanged(val period: Transaction.Period) : TransactionAction()
    data object ExpandPeriodClicked : TransactionAction()
    data object ExpandCategoryClicked : TransactionAction()
    data object ToggleDatePicker : TransactionAction()
    data object ToggleUntilDatePicker : TransactionAction()
    data class DateSelected(val date: LocalDate) : TransactionAction()
    data class DateUntilSelected(val date: LocalDate) : TransactionAction()
    data class CategoryChanged(val category: Category) : TransactionAction()
    data class CategoryCreate(val name: String) : TransactionAction()
    data class CategoryDelete(val category: Category?) : TransactionAction()
    data object SubmitClicked : TransactionAction()
}
