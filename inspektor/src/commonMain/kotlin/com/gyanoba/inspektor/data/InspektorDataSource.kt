package com.gyanoba.inspektor.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant


internal interface InspektorDataSource {
    suspend fun insertHttpTransaction(httpTransaction: HttpTransaction): Long
    fun getTransactionFlow(id: Long): Flow<HttpTransaction?>
    suspend fun getTransaction(id: Long): HttpTransaction?
    suspend fun updateHttpTransaction(httpTransaction: HttpTransaction)
    suspend fun getAllLatestHttpTransactionsForDateRange(
        startDate: Instant,
        endDate: Instant,
    ): List<HttpTransaction>

    fun getAllLatestHttpTransactionsForDateRangeFlow(
        startDate: Instant,
        endDate: Instant,
    ): Flow<List<HttpTransaction>>

    fun getAllLatestHttpTransactionsFilteredFlow(
        startDate: Instant,
        endDate: Instant,
        responseCode: String,
        path: String,
    ): Flow<List<GetAllLatestWithLimit>>

    fun getAllHttpTransactionsCount(): Flow<Long>
    suspend fun deleteBefore(timestamp: Instant)
}

internal class InspektorDataSourceImpl(
    private val db: InspektorDatabase,
) : InspektorDataSource {

    override suspend fun insertHttpTransaction(httpTransaction: HttpTransaction): Long =
        withContext(Dispatchers.IO) {
            db.transactionWithResult {
                db.httpTransactionQueries.insert(httpTransaction)
                db.httpTransactionQueries.lastInsertRowId().executeAsOne()
            }
        }

    override fun getTransactionFlow(id: Long): Flow<HttpTransaction> =
        db.httpTransactionQueries.getById(id).asFlow()
            .mapToOne(Dispatchers.IO)

    override suspend fun getTransaction(id: Long): HttpTransaction =
        db.httpTransactionQueries.getById(id)
            .executeAsOne()

    override suspend fun updateHttpTransaction(httpTransaction: HttpTransaction): Unit =
        withContext(Dispatchers.IO) {
            db.httpTransactionQueries.insertOrReplace(httpTransaction)
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

    override suspend fun deleteBefore(timestamp: Instant): Unit = withContext(Dispatchers.IO) {
        db.httpTransactionQueries.deleteBefore(timestamp)
    }

    companion object {
        val Instance by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            InspektorDataSourceImpl(createDatabase())
        }
    }
}