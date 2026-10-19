package utils

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gyanoba.inspektor.data.InspektorDatabase
import kotlinx.coroutines.runBlocking

actual fun createTempDbDriver(): SqlDriver = runBlocking {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    InspektorDatabase.Schema.create(driver).await()
    driver
}
