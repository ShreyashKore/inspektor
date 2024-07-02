package com.gyanoba.inspektor.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gyanoba.inspektor.db.InspectorDatabase
import kotlinx.coroutines.runBlocking
import java.io.File

internal actual object DriverFactory {
    actual fun createDbDriver(): SqlDriver = runBlocking {
        val driver: SqlDriver = JdbcSqliteDriver("jdbc:sqlite:${DB_NAME}")
        if (!databaseFile.exists()) {
            InspectorDatabase.Schema.create(driver).await()
        }
        driver
    }
}

private val databaseFile: File
    get() = File(appDir.also { if (!it.exists()) it.mkdirs() }, DB_NAME)

private val appDir: File
    get() {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("win") -> {
                File(System.getenv("AppData"), "tivi/db")
            }

            os.contains("nix") || os.contains("nux") || os.contains("aix") -> {
                File(System.getProperty("user.home"), ".tivi")
            }

            os.contains("mac") -> {
                File(System.getProperty("user.home"), "Library/Application Support/tivi")
            }

            else -> error("Unsupported operating system")
        }
    }
