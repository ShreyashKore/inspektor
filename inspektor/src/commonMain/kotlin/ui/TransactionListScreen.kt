package ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import ui.components.DateRangePickerDialog
import utils.DateFormatters
import utils.TimeFormatters
import utils.atLocalEndOfDay
import utils.atLocalStartOfDay

@Composable
internal fun TransactionListScreen(
    openTransaction: (HttpTransaction) -> Unit,
) {
    val viewModel = viewModel<TransactionViewModel> {
        TransactionViewModel()
    }
    TransactionListScreen(
        viewModel.transactions.collectAsState().value,
        openTransaction,
        viewModel.allCount.collectAsState().value,
        viewModel.startDate.collectAsState().value,
        viewModel.endDate.collectAsState().value,
        viewModel::onDateRangeSelected
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionListScreen(
    transactions: List<HttpTransaction> = emptyList(),
    onClickTransaction: (HttpTransaction) -> Unit,
    allCount: Long,
    startDate: Instant,
    endDate: Instant,
    onDateRangeSelected: (Instant, Instant) -> Unit,
) {
    var showDateRangePicker by remember { mutableStateOf(false) }

    if (showDateRangePicker) {
        DateRangePickerDialog(
            startDate = startDate,
            endDate = endDate,
            onDismissRequest = { showDateRangePicker = false },
            onConfirm = onDateRangeSelected,
        )
    }

    Scaffold(topBar = {
        CenterAlignedTopAppBar(title = { Text(text = "Transactions") })
    }) { paddingValues ->
        Column(Modifier.padding(paddingValues)) {
            Text(text = "Transactions: $allCount")
            TextButton(onClick = { showDateRangePicker = true }) {
                val startDateFormatted = startDate.toLocalDateTime(TimeZone.currentSystemDefault())
                    .format(DateFormatters.simpleLocalFormatter)
                val endDateFormatted = endDate.toLocalDateTime(TimeZone.currentSystemDefault())
                    .format(DateFormatters.simpleLocalFormatter)
                Text(
                    "$startDateFormatted - $endDateFormatted"
                )
            }
            LazyColumn {
                items(transactions) { transaction ->
                    TransactionItem(
                        transaction = transaction,
                        onClick = { onClickTransaction(transaction) },
                    )
                }
            }
        }

    }
}

@Composable
internal fun TransactionItem(
    transaction: HttpTransaction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = transaction.statusCode?.toString() ?: "",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(60.dp)
            )
            Column {
                Row {
                    Text(text = transaction.method, style = MaterialTheme.typography.bodyLarge)
                    Text(text = transaction.path ?: "")
                }
                Text(text = transaction.host ?: "")
                Row {
                    Text(
                        text = transaction.requestDate.toLocalDateTime(TimeZone.currentSystemDefault())
                            .format(TimeFormatters.simpleLocalAmPm)
                    )
                    Text(text = transaction.tookMs?.toString() ?: "")
                }
            }
        }

    }
}


internal class TransactionViewModel : ViewModel() {
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

    fun onDateRangeSelected(startDate: Instant, endDate: Instant) {
        _startDate.value = startDate.atLocalEndOfDay()
        _endDate.value = endDate.atLocalEndOfDay()
    }
}
