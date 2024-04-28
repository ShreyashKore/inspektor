package data.db

import android.annotation.SuppressLint
import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.gyanoba.inspektor.db.Database
import utils.ContextInitializer

@SuppressLint("StaticFieldLeak")
actual object DriverFactory {
    actual fun createDbDriver(): SqlDriver =
        AndroidSqliteDriver(
            Database.Schema,
            ContextInitializer.appContext,
            DB_NAME,
        )
}
