package data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gyanoba.inspektor.db.InspektorDatabase
import kotlinx.coroutines.runBlocking

internal actual object DriverFactory {
    actual fun createDbDriver(): SqlDriver = runBlocking {
        val driver: SqlDriver = JdbcSqliteDriver("jdbc:sqlite:${DB_NAME}")
        InspektorDatabase.Schema.create(driver).await()
        driver
    }
}
