package com.gyanoba.inspektor.data

import android.annotation.SuppressLint
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gyanoba.inspektor.UnstableInspektorAPI
import com.gyanoba.inspektor.utils.ContextInitializer
import kotlinx.coroutines.runBlocking

@SuppressLint("StaticFieldLeak")
internal actual object DriverFactory {
    actual fun createDbDriver(): SqlDriver =
        AndroidSqliteDriver(
            InspektorDatabase.Schema,
            ContextInitializer.appContext,
            DB_NAME,
        )

    actual fun createTempDbDriver(): SqlDriver = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        InspektorDatabase.Schema.create(driver).await()
        driver
    }
}

@UnstableInspektorAPI
public actual fun setApplicationId(applicationId: String) {
    // No-op
}