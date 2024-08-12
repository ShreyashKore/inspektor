package ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import data.InspektorDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import utils.atLocalEndOfDay
import utils.atLocalStartOfDay


internal class TransactionListViewModel : ViewModel() {
    private val inspektorDataSource = InspektorDataSource.Instance

    private val _startDate = MutableStateFlow(Clock.System.now().atLocalStartOfDay())
    val startDate = _startDate.asStateFlow()
    private val _endDate = MutableStateFlow(Clock.System.now().atLocalEndOfDay())
    val endDate = _endDate.asStateFlow()

    val allCount = inspektorDataSource.getAllHttpTransactionsCount().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(4000), 0
    )

    val transactions = combine(startDate, endDate, allCount) { startDate, endDate, _ ->
        inspektorDataSource.getAllLatestHttpTransactionsForDateRange(startDate, endDate)
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