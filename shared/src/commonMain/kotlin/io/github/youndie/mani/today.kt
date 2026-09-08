@file:OptIn(ExperimentalTime::class)

package io.github.youndie.mani

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Сегодняшний календарный день.
 *
 * Живёт в контракте, а не у клиента: по нему разворачивается сид песочницы и считается прогноз,
 * то есть он нужен и серверу.
 */
@Suppress(
    "ktlint:kapkan:wall-clock",
    "местный календарный день — ровно то, ради чего эта функция есть",
)
fun today() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
