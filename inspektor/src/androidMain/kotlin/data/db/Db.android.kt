package data.db

import android.annotation.SuppressLint
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.gyanoba.inspektor.db.InspektorDatabase
import com.gyanoba.inspektor.utils.ContextInitializer

@SuppressLint("StaticFieldLeak")
internal actual object DriverFactory {
    actual fun createDbDriver(): SqlDriver =
        AndroidSqliteDriver(
            InspektorDatabase.Schema,
            ContextInitializer.appContext,
            DB_NAME,
        )

    actual fun createTempDbDriver(): SqlDriver = TODO()
}
