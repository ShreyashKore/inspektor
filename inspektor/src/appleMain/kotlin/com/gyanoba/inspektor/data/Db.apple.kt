package com.gyanoba.inspektor.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.inMemoryDriver
import com.gyanoba.inspektor.UnstableInspektorAPI

internal actual object DriverFactory {
    actual fun createDbDriver(): SqlDriver =
        NativeSqliteDriver(InspektorDatabase.Schema, DB_NAME)

    actual fun createTempDbDriver(): SqlDriver = inMemoryDriver(InspektorDatabase.Schema)
}

@UnstableInspektorAPI
public actual fun setApplicationId(applicationId: String) {
    // No-op
}