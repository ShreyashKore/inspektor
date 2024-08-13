package data.db

import app.cash.sqldelight.db.SqlDriver
import com.gyanoba.inspektor.data.entites.HttpTransaction
import com.gyanoba.inspektor.db.InspektorDatabase
import data.adapters.instantAdapter
import data.db.adapters.setMapEntryAdapter

internal const val DB_NAME = "com.gyanoba.inspektor.db"

internal expect object DriverFactory {
    fun createDbDriver(): SqlDriver
}

internal fun createDatabase(): InspektorDatabase {
    val driver = DriverFactory.createDbDriver()
    return InspektorDatabase(
        driver, HttpTransaction.Adapter(
            requestDateAdapter = instantAdapter,
            responseDateAdapter = instantAdapter,
            requestHeadersAdapter = setMapEntryAdapter,
            responseHeadersAdapter = setMapEntryAdapter
        )
    )
}