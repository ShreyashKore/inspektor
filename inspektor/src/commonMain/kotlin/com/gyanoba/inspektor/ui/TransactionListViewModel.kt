package com.gyanoba.inspektor.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gyanoba.inspektor.data.InspektorDataSource
import com.gyanoba.inspektor.har.toHarLog
import com.gyanoba.inspektor.platform.FileSharer
import com.gyanoba.inspektor.platform.Os
import com.gyanoba.inspektor.platform.currentOs
import com.gyanoba.inspektor.platform.getAppCacheDir
import com.gyanoba.inspektor.utils.atLocalEndOfDay
import com.gyanoba.inspektor.utils.atLocalStartOfDay
import io.ktor.utils.io.core.writeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.time.Duration.Companion.days


internal class TransactionListViewModel(
    private val inspektorDataSource: InspektorDataSource,
    private val fileSharer: FileSharer,
) : ViewModel() {

    private val _startDate = MutableStateFlow((Clock.System.now() - 6.days).atLocalStartOfDay())
    val startDate = _startDate.asStateFlow()
    private val _endDate = MutableStateFlow(Clock.System.now().atLocalEndOfDay())
    val endDate = _endDate.asStateFlow()

    val searchFieldState = TextFieldState()
    private val searchFlow = snapshotFlow { searchFieldState.text }

    val allCount = inspektorDataSource.getAllHttpTransactionsCount().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(4000), 0
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val transactions = combine(startDate, endDate, searchFlow) { startDate, endDate, searchTerm ->
        (startDate to endDate) to searchTerm
    }.flatMapLatest { (dates, searchTerm) ->
        val (startDate, endDate) = dates
        val responseCode = if (searchTerm.isDigitsOnly()) searchTerm else ""
        val path = if (responseCode.isNotEmpty()) "" else searchTerm

        inspektorDataSource.getAllLatestHttpTransactionsFilteredFlow(
            startDate,
            endDate,
            "$responseCode",
            "$path"
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(4000), emptyList())


    fun deleteTransactions(beforeDate: Instant) {
        viewModelScope.launch {
            inspektorDataSource.deleteBefore(beforeDate)
        }
    }

    fun onDateRangeSelected(startDate: Instant, endDate: Instant) {
        _startDate.value = startDate.atLocalStartOfDay()
        _endDate.value = endDate.atLocalEndOfDay()
    }

    fun shareAsHar() {
        viewModelScope.launch {
            inspektorDataSource.getAllLatestHttpTransactionsForDateRange(
                startDate.value,
                endDate.value
            ).let { transactions ->
                val harFileContent = transactions.toHarLog(
                    creatorName = "Inspektor",
                )
                // macOS won't open .har files if supporting application is not installed
                val fileExtension =
                    if (currentOs is Os.Desktop.MACOS) "txt" else "har"

                val harFilePath = "${getAppCacheDir()}/inspektor_share/inspektor.$fileExtension"
                createTextFile(
                    filePath = harFilePath,
                    text = harFileContent
                )

                fileSharer.shareFile(harFilePath, "text/plain")
            }
        }
    }
}

private fun CharSequence.isDigitsOnly(): Boolean {
    return all { it.isDigit() }
}


internal suspend fun createTextFile(filePath: String, text: String) = withContext(Dispatchers.IO) {
    val path = Path(filePath)
    try {
        SystemFileSystem.createDirectories(path.parent!!)
        SystemFileSystem.sink(path).buffered().use { sink ->
            sink.writeText(text)
        }
        println("Successfully wrote text to $filePath")
    } catch (e: IOException) {
        println("Error writing to $filePath: ${e.message}")
        throw e
    } catch (e: Exception) {
        println("An unexpected error occurred: ${e.message}")
        throw e
    }
}