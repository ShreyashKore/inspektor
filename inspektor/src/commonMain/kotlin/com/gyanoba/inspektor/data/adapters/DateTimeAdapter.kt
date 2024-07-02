package com.gyanoba.inspektor.data.adapters

import app.cash.sqldelight.ColumnAdapter
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

internal val dateTimeAdapter = object : ColumnAdapter<LocalDateTime, Long> {
    override fun decode(databaseValue: Long): LocalDateTime =
        Instant.fromEpochMilliseconds(databaseValue).toLocalDateTime(TimeZone.UTC)

    override fun encode(value: LocalDateTime): Long =
        value.toInstant(TimeZone.UTC).toEpochMilliseconds()
}

internal val instantAdapter = object : ColumnAdapter<Instant, Long> {
    override fun decode(databaseValue: Long): Instant = Instant.fromEpochMilliseconds(databaseValue)
    override fun encode(value: Instant): Long = value.toEpochMilliseconds()
}