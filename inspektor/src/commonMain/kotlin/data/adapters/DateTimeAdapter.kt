package data.adapters

import app.cash.sqldelight.ColumnAdapter
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

val dateTimeAdapter = object : ColumnAdapter<LocalDateTime, Long> {
    override fun decode(databaseValue: Long): LocalDateTime =
        Instant.fromEpochMilliseconds(databaseValue).toLocalDateTime(TimeZone.UTC)

    override fun encode(value: LocalDateTime): Long =
        value.toInstant(TimeZone.UTC).toEpochMilliseconds()
}