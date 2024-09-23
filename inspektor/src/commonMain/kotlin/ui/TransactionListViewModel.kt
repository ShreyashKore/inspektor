package ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import data.InspektorDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import utils.atLocalEndOfDay
import utils.atLocalStartOfDay


internal class TransactionListViewModel(
    private val inspektorDataSource: InspektorDataSource,
) : ViewModel() {

    private val _startDate = MutableStateFlow(Clock.System.now().atLocalStartOfDay())
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
}

private fun CharSequence.isDigitsOnly(): Boolean {
    return all { it.isDigit() }
}