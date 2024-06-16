package data.db

import app.cash.sqldelight.db.SqlDriver
import com.gyanoba.inspektor.data.entites.HttpTransaction
import com.gyanoba.inspektor.db.Database
import data.adapters.instantAdapter

internal const val DB_NAME = "com.gyanoba.inspektor.db"

internal expect object DriverFactory {
    fun createDbDriver(): SqlDriver
}

internal fun createDatabase(): Database {
    val driver = DriverFactory.createDbDriver()
    return Database(
        driver, HttpTransaction.Adapter(
            requestDateAdapter = instantAdapter,
            responseDateAdapter = instantAdapter
        )
    )
}