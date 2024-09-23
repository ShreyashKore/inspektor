package data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.gyanoba.inspektor.db.InspektorDatabase

internal actual object DriverFactory {
    actual fun createDbDriver(): SqlDriver =
        NativeSqliteDriver(InspektorDatabase.Schema, DB_NAME)

    actual fun createTempDbDriver(): SqlDriver = TODO()
}
