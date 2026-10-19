package com.gyanoba.inspektor

import com.gyanoba.inspektor.data.InspektorDataSource
import com.gyanoba.inspektor.utils.InspektorLog
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Deletes transactions older than [retentionDuration], at most once per [cleanupFrequency].
 *
 * Every integration calls [checkAndCleanUp] as calls go out, so the store cannot grow without
 * bound whichever client the consumer uses.
 */
@UnstableInspektorAPI
public class RetentionManager(
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

    /**
     * Fire-and-forget [checkAndCleanUp], for integrations that run on blocking threads -- an
     * OkHttp interceptor or a URLSession delegate callback cannot suspend.
     */
    @OptIn(DelicateCoroutinesApi::class)
    public fun checkAndCleanUpAsync(): Job = GlobalScope.launch(Dispatchers.IO) { checkAndCleanUp() }

    public suspend fun checkAndCleanUp() {
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
            InspektorLog.info("RetentionManager") {
                "Cleaning up transactions older than $deleteBefore (retention duration: $retentionDuration)"
            }
            dataSource.deleteBefore(deleteBefore)
        } catch (e: Exception) {
            InspektorLog.info("RetentionManager") { "Error during cleanup: ${e.message}" }
        }
    }

}