package com.gyanoba.inspektor.data

import app.cash.sqldelight.db.SqlDriver
import com.gyanoba.inspektor.UnstableInspektorAPI
import com.gyanoba.inspektor.data.adapters.instantAdapter
import com.gyanoba.inspektor.data.adapters.setMapEntryAdapter

internal const val DB_NAME = "com.gyanoba.inspektor.db"

internal expect object DriverFactory {
    fun createDbDriver(): SqlDriver
}

internal fun createDatabase(): InspektorDatabase =
    createInspektorDatabase(DriverFactory.createDbDriver())

/**
 * Wraps [driver] with Inspektor's column adapters.
 *
 * Public so that tests -- in this module and in every integration module -- can build a database on
 * an in-memory driver without restating the adapter wiring, which used to drift out of sync.
 */
@UnstableInspektorAPI
public fun createInspektorDatabase(driver: SqlDriver): InspektorDatabase = InspektorDatabase(
    driver, HttpTransaction.Adapter(
        requestDateAdapter = instantAdapter,
        responseDateAdapter = instantAdapter,
        requestHeadersAdapter = setMapEntryAdapter,
        responseHeadersAdapter = setMapEntryAdapter,
        originalResponseHeadersAdapter = setMapEntryAdapter,
        originalRequestHeadersAdapter = setMapEntryAdapter,
    )
)

@UnstableInspektorAPI
public expect fun setApplicationId(applicationId: String)