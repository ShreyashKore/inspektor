package com.gyanoba.inspektor.data

import app.cash.sqldelight.db.SqlDriver
import com.gyanoba.inspektor.UnstableInspektorAPI
import com.gyanoba.inspektor.data.adapters.instantAdapter
import com.gyanoba.inspektor.data.adapters.setMapEntryAdapter

internal const val DB_NAME = "com.gyanoba.inspektor.db"

internal expect object DriverFactory {
    fun createDbDriver(): SqlDriver

    fun createTempDbDriver(): SqlDriver
}

internal fun createDatabase(): InspektorDatabase {
    val driver = DriverFactory.createDbDriver()
    return InspektorDatabase(
        driver, HttpTransaction.Adapter(
            requestDateAdapter = instantAdapter,
            responseDateAdapter = instantAdapter,
            requestHeadersAdapter = setMapEntryAdapter,
            responseHeadersAdapter = setMapEntryAdapter,
            replacedResponseHeadersAdapter = setMapEntryAdapter,
            replacedRequestHeadersAdapter = setMapEntryAdapter,
        )
    )
}

@UnstableInspektorAPI
public expect fun setApplicationId(applicationId: String)