package com.gyanoba.inspektor.data.db

import app.cash.sqldelight.db.SqlDriver
import com.gyanoba.inspektor.data.adapters.instantAdapter
import com.gyanoba.inspektor.data.entites.HttpTransaction
import com.gyanoba.inspektor.db.InspectorDatabase

internal const val DB_NAME = "inspektor.db"

internal expect object DriverFactory {
    fun createDbDriver(): SqlDriver
}

internal fun createDatabase(): InspectorDatabase {
    val driver = DriverFactory.createDbDriver()
    return InspectorDatabase(
        driver, HttpTransaction.Adapter(
            requestDateAdapter = instantAdapter,
            responseDateAdapter = instantAdapter
        )
    )
}