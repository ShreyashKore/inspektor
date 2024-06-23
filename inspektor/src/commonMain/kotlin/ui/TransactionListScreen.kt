package ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gyanoba.inspektor.data.entites.HttpTransaction
import data.InspektorDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Composable
internal fun TransactionScreen() {
    val viewModel = viewModel<TransactionViewModel>()
    TransactionListScreen(
        viewModel.transactions.collectAsState().value,
        viewModel.allCount.collectAsState().value,
        viewModel.startDate.collectAsState().value,
        viewModel.endDate.collectAsState().value
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionListScreen(
    transactions: List<HttpTransaction> = emptyList(),
    allCount: Long = 0,
    startDate: Instant,
    endDate: Instant,
) {
    val dateRangePickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = startDate.toEpochMilliseconds(),
        initialSelectedEndDateMillis = endDate.toEpochMilliseconds(),
    )

    snapshotFlow { }
    dateRangePickerState.selectedEndDateMillis
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Transactions") }

            )
        }
    ) {
        Column {
            Text(text = "AllCount: $allCount")
            DateRangePicker(dateRangePickerState)
            LazyColumn {
                items(transactions) {
                    TransactionItem(it)
                }
            }
        }

    }
}

@Composable
internal fun TransactionItem(transaction: HttpTransaction) {
    Text(text = transaction.toString())
}

internal class TransactionViewModel : ViewModel() {
    private val inspektorDataSource = InspektorDataSource.Instance

    private val _startDate = MutableStateFlow(Clock.System.now())
    val startDate = _startDate.asStateFlow()
    private val _endDate = MutableStateFlow(Clock.System.now())
    val endDate = _endDate.asStateFlow()

    val allCount = inspektorDataSource.getAllHttpTransactionsCount().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(4000),
        0
    )

    val transactions = combine(startDate, endDate, allCount) { startDate, endDate, _ ->
        inspektorDataSource.getAllLatestHttpTransactionsForDateRange(startDate, endDate)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(4000), emptyList())

}
