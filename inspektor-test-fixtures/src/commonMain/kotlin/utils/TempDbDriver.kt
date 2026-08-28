package utils

import app.cash.sqldelight.db.SqlDriver

/**
 * An in-memory driver for tests.
 *
 * This deliberately lives in the test source sets rather than in `DriverFactory`: the Android
 * implementation needs `JdbcSqliteDriver`, which bundles ~6 MB of desktop SQLite binaries (macOS
 * `.dylib`, Windows `.dll`). Declaring it in `androidMain` packaged all of that into every
 * consumer's APK for the sake of a helper only tests ever call.
 */
expect fun createTempDbDriver(): SqlDriver
