@file:OptIn(ExperimentalTime::class)

package ru.workinprogress.mani

import kotlinx.collections.immutable.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

fun <T> emptyImmutableList(): ImmutableList<T> = persistentListOf()
fun <K, V> emptyImmutableMap(): ImmutableMap<K, V> = persistentMapOf()
fun <T> emptyImmutableSet(): ImmutableSet<T> = persistentSetOf()

fun today() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
val LocalDate?.orToday get() = this ?: today()
val defaultMinDate get() = today().minus(1, DateTimeUnit.MONTH)
