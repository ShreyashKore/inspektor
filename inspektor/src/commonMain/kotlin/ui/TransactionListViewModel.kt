package ui

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

    val allCount = inspektorDataSource.getAllHttpTransactionsCount().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(4000), 0
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val transactions = combine(startDate, endDate) { startDate, endDate ->
        (startDate to endDate)
    }.flatMapLatest { (startDate, endDate) ->
        inspektorDataSource.getAllLatestHttpTransactionsForDateRangeFlow(startDate, endDate)
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
