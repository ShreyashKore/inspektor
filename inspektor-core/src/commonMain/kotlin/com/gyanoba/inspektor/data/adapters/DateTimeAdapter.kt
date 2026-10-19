package com.gyanoba.inspektor.data.adapters

import app.cash.sqldelight.ColumnAdapter
import kotlin.time.Instant

internal val instantAdapter = object : ColumnAdapter<Instant, Long> {
    override fun decode(databaseValue: Long): Instant = Instant.fromEpochMilliseconds(databaseValue)
    override fun encode(value: Instant): Long = value.toEpochMilliseconds()
}