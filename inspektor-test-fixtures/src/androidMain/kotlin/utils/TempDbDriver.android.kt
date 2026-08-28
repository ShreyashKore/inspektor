package utils

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gyanoba.inspektor.data.InspektorDatabase
import kotlinx.coroutines.runBlocking

// Android unit tests run on the host JVM, where android.database is stubbed out, so the JDBC
// driver is used here. It is a test-only dependency and never reaches the published AAR.
actual fun createTempDbDriver(): SqlDriver = runBlocking {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    InspektorDatabase.Schema.create(driver).await()
    driver
}
