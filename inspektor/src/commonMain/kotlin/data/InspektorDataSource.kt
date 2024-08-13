package data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.gyanoba.inspektor.data.entites.HttpTransaction
import data.db.createDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant


internal class InspektorDataSource private constructor() {
    private val db = createDatabase()

    suspend fun insertHttpTransaction(httpTransaction: HttpTransaction) =
        withContext(Dispatchers.IO) {
            db.transactionWithResult {
                db.httpTransactionQueries.insert(httpTransaction)
                db.httpTransactionQueries.lastInsertRowId().executeAsOne()
            }
        }

    fun getTransaction(id: Long) =
        db.httpTransactionQueries.getById(id).asFlow()
            .mapToOne(Dispatchers.IO)

    suspend fun updateHttpTransaction(httpTransaction: HttpTransaction) =
        withContext(Dispatchers.IO) {
            db.httpTransactionQueries.insertOrReplace(httpTransaction)
        }


    suspend fun getAllLatestHttpTransactionsForDateRange(startDate: Instant, endDate: Instant) =
        withContext(Dispatchers.IO) {
            db.httpTransactionQueries.getAllLatestForDateRange(startDate, endDate).executeAsList()
        }

    fun getAllLatestHttpTransactionsForDateRangeFlow(startDate: Instant, endDate: Instant) =
        db.httpTransactionQueries.getAllLatestForDateRange(startDate, endDate).asFlow()
            .mapToList(Dispatchers.IO)


    fun getAllHttpTransactionsCount() =
        db.httpTransactionQueries.getAllCount().asFlow()
            .mapToOne(Dispatchers.IO)

    suspend fun deleteBefore(timestamp: Instant) = withContext(Dispatchers.IO) {
        db.httpTransactionQueries.deleteBefore(timestamp)
    }

    companion object {
        val Instance by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) { InspektorDataSource() }
    }
}