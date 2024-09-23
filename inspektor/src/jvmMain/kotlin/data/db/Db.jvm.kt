package data.db

import UnstableInspektorAPI
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gyanoba.inspektor.db.InspektorDatabase
import kotlinx.coroutines.runBlocking
import platform.getAppDataDir
import utils.log
import java.io.File
import java.nio.file.Paths

internal actual object DriverFactory {
    actual fun createDbDriver(): SqlDriver = runBlocking {
        val dbPath = getDatabasePath()
        val driver: SqlDriver = JdbcSqliteDriver("jdbc:sqlite:$dbPath")
        InspektorDatabase.Schema.create(driver).await()
        driver
    }

    actual fun createTempDbDriver(): SqlDriver = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        InspektorDatabase.Schema.create(driver).await()
        driver
    }
}


@OptIn(UnstableInspektorAPI::class)
private fun getDatabasePath(): String {
    require(APPLICATION_ID != null) {
        "Application ID must be provided for the desktop platforms"
    }
    val dbPath = Paths.get(getAppDataDir(APPLICATION_ID!!), DB_NAME).toString()
    File(dbPath).parentFile.mkdirs()
    return dbPath
}

/**
 * Application ID is used to resolve the folder in which database will be stored.
 */
@UnstableInspektorAPI
public var APPLICATION_ID: String? = null
    set(value) {
        if (field != null && field != value) {
            log("Inspektor") {
                "Application ID has already been set to $field" +
                        "It should not be changed."
            }
        }
        field = value
    }