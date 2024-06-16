package data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

internal actual object DriverFactory {
    actual fun createDbDriver(): SqlDriver =
        JdbcSqliteDriver(DB_NAME)
}
