package data.db

import android.annotation.SuppressLint
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.gyanoba.inspektor.db.Database
import com.gyanoba.inspektor.utils.ContextInitializer

@SuppressLint("StaticFieldLeak")
internal actual object DriverFactory {
    actual fun createDbDriver(): SqlDriver =
        AndroidSqliteDriver(
            Database.Schema,
            ContextInitializer.appContext,
            DB_NAME,
        )
}
