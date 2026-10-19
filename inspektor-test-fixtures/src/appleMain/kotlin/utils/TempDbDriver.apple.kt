package utils

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.inMemoryDriver
import com.gyanoba.inspektor.data.InspektorDatabase

actual fun createTempDbDriver(): SqlDriver = inMemoryDriver(InspektorDatabase.Schema)
