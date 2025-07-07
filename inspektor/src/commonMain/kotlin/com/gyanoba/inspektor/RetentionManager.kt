package com.gyanoba.inspektor

import com.gyanoba.inspektor.data.InspektorDataSource
import com.gyanoba.inspektor.utils.log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

internal class RetentionManager(
    private val retentionDuration: Duration,
    private val dataSource: InspektorDataSource,
    private val clock: Clock = Clock.System,
) {
    private val mutex = Mutex()

    private var lastCleanupTime: Instant = Instant.DISTANT_PAST

    private val cleanupFrequency = when (retentionDuration) {
        in 0.minutes..60.minutes -> 1.minutes
        else -> 10.minutes
    }

    suspend fun checkAndCleanUp() {
        val currentTime = clock.now()
        mutex.withLock {
            val isCleanupDue = currentTime - lastCleanupTime > cleanupFrequency
            if (isCleanupDue) {
                cleanUpOldTransactions()
                lastCleanupTime = currentTime
            }
        }
    }

    private suspend fun cleanUpOldTransactions() {
        try {
            val currentTime = clock.now()
            val deleteBefore = currentTime - retentionDuration
            log("RetentionManager") {
                "Cleaning up transactions older than $deleteBefore (retention duration: $retentionDuration)"
            }
            dataSource.deleteBefore(deleteBefore)
        } catch (e: Exception) {
            log("RetentionManager") { "Error during cleanup: ${e.message}" }
        }
    }

}