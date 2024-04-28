package data.db

import app.cash.sqldelight.db.SqlDriver
import com.gyanoba.inspektor.db.Database

const val DB_NAME = "inspektor.db"

expect object DriverFactory {
    fun createDbDriver(): SqlDriver
}

fun createDatabase() :Database{
    val  driver = DriverFactory.createDbDriver()
    return Database(driver)
}