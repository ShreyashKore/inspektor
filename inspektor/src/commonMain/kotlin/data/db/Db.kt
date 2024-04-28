package data.db

import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import com.gyanoba.inspektor.data.entites.HttpTransaction
import com.gyanoba.inspektor.db.Database
import data.adapters.dateTimeAdapter

const val DB_NAME = "inspektor.db"

expect object DriverFactory {
    fun createDbDriver(): SqlDriver
}

fun createDatabase(): Database {
    val driver = DriverFactory.createDbDriver()
    return Database(
        driver, HttpTransaction.Adapter(
            methodAdapter = EnumColumnAdapter(),
            requestDateAdapter = dateTimeAdapter, responseDateAdapter = dateTimeAdapter
        )
    )
}