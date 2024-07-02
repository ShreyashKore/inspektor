package com.gyanoba.inspektor.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.gyanoba.inspektor.db.InspectorDatabase

internal actual object DriverFactory {
    actual fun createDbDriver(): SqlDriver =
        NativeSqliteDriver(InspectorDatabase.Schema, DB_NAME)
}
