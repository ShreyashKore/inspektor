package com.gyanoba.inspektor

import com.gyanoba.inspektor.data.InspektorDataSource
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private const val PREFS_NAME = "com.gyanoba.inspektor.preferences"
private const val KEY_LAST_CLEANUP = "last_cleanup"

private var lastCleanup: Instant = Instant.DISTANT_PAST

/**
 * Class responsible of holding the logic for the retention of your HTTP transactions.
 * You can customize how long data should be stored here.
 * @param context An Android Context
 * @param retentionPeriod A [Period] to specify the retention of data. Default 1 week.
 */
internal class CleanupManager(
    private val prefs: Settings,
    val retentionPeriod: Duration = 7.days,
    val db: InspektorDataSource,
) {
    init {
        require(retentionPeriod >= 1.hours) { "Retention Period must be at least 1 hour" }
    }


    private val cleanupFrequency: Duration = when (retentionPeriod) {
        in 1.hours..2.hours -> 30.minutes
        else -> 1.hours
    }

    private val cleanupMutex = Mutex()

    /**
     * Call this function to check and eventually trigger a cleanup.
     * Please note that this method is not forcing a cleanup.
     */
    internal suspend fun doCleanupIfNeeded() {
        if (retentionPeriod == Duration.INFINITE) return
        cleanupMutex.withLock {
            val now = Clock.System.now()
            val isCleanupDue = now - getLastCleanup(now) > cleanupFrequency
            if (isCleanupDue) {
                val deleteSince = now - retentionPeriod
                db.deleteBefore(deleteSince)
                setLastCleanup(now)
            }
        }
    }

    private fun getLastCleanup(fallback: Instant): Instant {
        if (lastCleanup == Instant.DISTANT_PAST) { // last cleanup not set yet
            val lastCleanupMillis = prefs.getLong(KEY_LAST_CLEANUP, fallback.toEpochMilliseconds())
            setLastCleanup(Instant.fromEpochMilliseconds(lastCleanupMillis))
        }
        return lastCleanup
    }

    private fun setLastCleanup(time: Instant) {
        lastCleanup = time
        prefs[KEY_LAST_CLEANUP] = time.toEpochMilliseconds()
    }
}