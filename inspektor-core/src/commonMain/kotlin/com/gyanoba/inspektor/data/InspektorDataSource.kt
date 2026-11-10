package com.gyanoba.inspektor.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import com.gyanoba.inspektor.UnstableInspektorAPI
import kotlin.time.Instant


@UnstableInspektorAPI
public interface InspektorDataSource {
    public suspend fun insertHttpTransaction(httpTransaction: HttpTransaction): Long
    public fun getTransactionFlow(id: Long): Flow<HttpTransaction?>
    public suspend fun getTransaction(id: Long): HttpTransaction?
    public suspend fun updateHttpTransaction(httpTransaction: HttpTransaction)
    public suspend fun getAllLatestHttpTransactionsForDateRange(
        startDate: Instant,
        endDate: Instant,
    ): List<HttpTransaction>

    public fun getAllLatestHttpTransactionsForDateRangeFlow(
        startDate: Instant,
        endDate: Instant,
    ): Flow<List<HttpTransaction>>

    public fun getAllLatestHttpTransactionsFilteredFlow(
        startDate: Instant,
        endDate: Instant,
        responseCode: String,
        path: String,
    ): Flow<List<GetAllLatestWithLimit>>

    public fun getAllHttpTransactionsCount(): Flow<Long>
    public suspend fun deleteBefore(timestamp: Instant)
}

@UnstableInspektorAPI
public class InspektorDataSourceImpl(
    private val db: InspektorDatabase,
) : InspektorDataSource {

    // Never suspends a writer: recording must not wait on a slow reader. A reader that falls more
    // than the buffer behind loses the oldest changes -- see TransactionChange.
    private val _changes = MutableSharedFlow<TransactionChange>(
        extraBufferCapacity = 1024,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Every write to the store, as it happens. Drives remote inspection. */
    internal val changes: SharedFlow<TransactionChange> get() = _changes

    override suspend fun insertHttpTransaction(httpTransaction: HttpTransaction): Long =
        withContext(Dispatchers.IO) {
            db.transactionWithResult {
                db.httpTransactionQueries.insert(httpTransaction)
                db.httpTransactionQueries.lastInsertRowId().executeAsOne()
            }
        }.also { _changes.tryEmit(TransactionChange.Inserted(it)) }

    override fun getTransactionFlow(id: Long): Flow<HttpTransaction?> =
        db.httpTransactionQueries.getById(id).asFlow()
            .mapToOneOrNull(Dispatchers.IO)

    override suspend fun getTransaction(id: Long): HttpTransaction? =
        db.httpTransactionQueries.getById(id)
            .executeAsOneOrNull()

    override suspend fun updateHttpTransaction(httpTransaction: HttpTransaction) {
        withContext(Dispatchers.IO) {
            db.httpTransactionQueries.insertOrReplace(httpTransaction)
        }
        _changes.tryEmit(TransactionChange.Updated(httpTransaction.id))
    }


    override suspend fun getAllLatestHttpTransactionsForDateRange(
        startDate: Instant,
        endDate: Instant,
    ): List<HttpTransaction> =
        withContext(Dispatchers.IO) {
            db.httpTransactionQueries.getAllLatestForDateRange(startDate, endDate).executeAsList()
        }

    override fun getAllLatestHttpTransactionsForDateRangeFlow(
        startDate: Instant,
        endDate: Instant,
    ): Flow<List<HttpTransaction>> =
        db.httpTransactionQueries.getAllLatestForDateRange(startDate, endDate).asFlow()
            .mapToList(Dispatchers.IO)

    override fun getAllLatestHttpTransactionsFilteredFlow(
        startDate: Instant,
        endDate: Instant,
        responseCode: String,
        path: String,
    ): Flow<List<GetAllLatestWithLimit>> {
        val responseCodeQuery = "$responseCode%"
        val pathQuery = if (path.isNotEmpty()) "%$path%" else "%"
        return db.httpTransactionQueries.getAllLatestWithLimit(
            startDate,
            endDate,
            responseCodeQuery,
            pathQuery
        )
            .asFlow()
            .mapToList(Dispatchers.IO)
    }


    override fun getAllHttpTransactionsCount(): Flow<Long> =
        db.httpTransactionQueries.getAllCount().asFlow()
            .mapToOne(Dispatchers.IO)

    override suspend fun deleteBefore(timestamp: Instant) {
        withContext(Dispatchers.IO) {
            db.httpTransactionQueries.deleteBefore(timestamp)
        }
        _changes.tryEmit(TransactionChange.Removed)
    }

    /** Up to [limit] transactions with an id above [afterId], oldest first. */
    internal suspend fun transactionsAfter(afterId: Long, limit: Long): List<HttpTransaction> =
        withContext(Dispatchers.IO) {
            db.httpTransactionQueries.getAfterId(afterId, limit).executeAsList()
        }

    /** The id of the oldest transaction still stored, or null when there are none. */
    internal suspend fun oldestTransactionId(): Long? = withContext(Dispatchers.IO) {
        db.httpTransactionQueries.getFirst().executeAsOneOrNull()?.id
    }

    internal suspend fun transactionCount(): Long = withContext(Dispatchers.IO) {
        db.httpTransactionQueries.getAllCount().executeAsOne()
    }

    internal suspend fun deleteAllTransactions() {
        withContext(Dispatchers.IO) {
            db.httpTransactionQueries.deleteAll()
        }
        _changes.tryEmit(TransactionChange.Removed)
    }

    public companion object {
        public val Instance: InspektorDataSourceImpl by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            InspektorDataSourceImpl(createDatabase())
        }
    }
}

/**
 * One write to [InspektorDataSourceImpl], for readers that follow the store live.
 *
 * Changes are a hint to re-read, not a log: a reader loads the row by id when it gets one, so it
 * always sends the latest state, and [Removed] says only that something was deleted.
 */
internal sealed interface TransactionChange {
    data class Inserted(val id: Long) : TransactionChange
    data class Updated(val id: Long) : TransactionChange
    data object Removed : TransactionChange
}
