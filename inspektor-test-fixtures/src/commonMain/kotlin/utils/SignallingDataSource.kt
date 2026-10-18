package utils

import com.gyanoba.inspektor.data.HttpTransaction
import com.gyanoba.inspektor.data.InspektorDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

/**
 * Wraps [delegate] and signals every write, so a test can wait for a recorder's off-thread writes
 * instead of sleeping, polling the table, or reaching into the recorder itself.
 *
 * A recorder inserts the row when the request is logged and updates it when the response is, both
 * on `Dispatchers.IO`. Each await consumes one write, so call it once per recorded call.
 */
class SignallingDataSource(
    private val delegate: InspektorDataSource,
) : InspektorDataSource by delegate {
    private val inserts = Channel<Unit>(Channel.UNLIMITED)
    private val updates = Channel<Unit>(Channel.UNLIMITED)

    override suspend fun insertHttpTransaction(httpTransaction: HttpTransaction): Long =
        delegate.insertHttpTransaction(httpTransaction).also { inserts.send(Unit) }

    override suspend fun updateHttpTransaction(httpTransaction: HttpTransaction) {
        delegate.updateHttpTransaction(httpTransaction)
        updates.send(Unit)
    }

    /** Suspends until one more call's request has been written. */
    suspend fun awaitRequestLogged(): Unit = await(inserts)

    /** Suspends until one more call's response has been written. */
    suspend fun awaitResponseLogged(): Unit = await(updates)

    // Real time even under `runTest`: the writes happen on `Dispatchers.IO`, which the virtual clock
    // knows nothing about, so a virtual-time timeout would fire before any of them landed.
    private suspend fun await(writes: Channel<Unit>): Unit = withContext(Dispatchers.Default) {
        withTimeout(5.seconds) { writes.receive() }
    }
}
