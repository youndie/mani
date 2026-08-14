package ru.workinprogress.feature.transaction.ui.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.datetime.LocalDate

typealias TransactionsByDays = ImmutableMap<LocalDate, ImmutableList<TransactionUiItem>>
