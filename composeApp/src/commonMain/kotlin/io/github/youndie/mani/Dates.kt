package io.github.youndie.mani

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/*
 * Мелочи, которыми пользуются только экраны: пустые неизменяемые коллекции для состояний и
 * умолчания дат для форм. Раньше лежали в `:shared` рядом с контрактом — сервер тянул за ними
 * `kotlinx-collections-immutable`, который ему не нужен ни для чего.
 */

fun <T> emptyImmutableList(): ImmutableList<T> = persistentListOf()
fun <K, V> emptyImmutableMap(): ImmutableMap<K, V> = persistentMapOf()
fun <T> emptyImmutableSet(): ImmutableSet<T> = persistentSetOf()

val LocalDate?.orToday get() = this ?: today()
val defaultMinDate get() = today().minus(1, DateTimeUnit.MONTH)
