package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gyanoba.inspektor.data.entites.HttpTransaction
import data.InspektorDataSourceImpl
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import platform.getAppName
import ui.components.DateRangeButton
import ui.components.DateRangePickerDialog
import ui.theme.errorColor
import ui.theme.successColor
import ui.theme.warningColor
import utils.TimeFormatters

@Composable
internal fun TransactionListScreen(
    openTransaction: (HttpTransaction) -> Unit,
) {
    val viewModel = viewModel<TransactionListViewModel> {
        TransactionListViewModel(InspektorDataSourceImpl.Instance)
    }
    TransactionListScreen(
        viewModel.transactions.collectAsState().value,
        openTransaction,
        viewModel.allCount.collectAsState().value,
        viewModel.startDate.collectAsState().value,
        viewModel.endDate.collectAsState().value,
        viewModel::onDateRangeSelected,
        viewModel::deleteTransactions
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
    onDeleteTransactions: (Instant) -> Unit,
) {
    var showDateRangePicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDateRangePicker) {
        DateRangePickerDialog(
            startDate = startDate,
            endDate = endDate,
            onDismissRequest = { showDateRangePicker = false },
            onConfirm = onDateRangeSelected,
        )
    }

    if (showDeleteDialog) {
        DeleteDialog(
            onDismissRequest = { showDeleteDialog = false },
            onConfirm = {
                onDeleteTransactions(Clock.System.now())
                showDeleteDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    val appName = remember(Unit) { getAppName() }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Inspektor",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        appName?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.alpha(0.5f)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Transactions", style = MaterialTheme.typography.labelSmall)
                    Text(text = "${transactions.size} of $allCount")
                }
                Spacer(Modifier.weight(1f))
                DateRangeButton(
                    startDate = startDate,
                    endDate = endDate,
                    onClick = { showDateRangePicker = true }
                )
            }

            LazyColumn {
                items(transactions) { transaction ->
                    TransactionItem(
                        transaction = transaction,
                        onClick = { onClickTransaction(transaction) },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            StatusCodeView(
                transaction.responseCode,
                Modifier.width(60.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = transaction.method ?: "",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = transaction.path ?: "",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600)
                    )
                }
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface.copy(
                        .6f
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (transaction.scheme == "https") {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Secure",
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = transaction.host ?: "",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Row {
                        Text(
                            text = transaction.requestDate?.toLocalDateTime(TimeZone.currentSystemDefault())
                                ?.format(TimeFormatters.simpleLocalAmPm) ?: ""
                        )
                        Spacer(Modifier.weight(1f))
                        Text(text = (transaction.tookMs?.toString() ?: "--") + " ms")
                    }
                }

            }
        }

    }
}

@Composable
internal fun StatusCodeView(
    statusCode: Long?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = when {
            statusCode == null -> MaterialTheme.colorScheme.onSurface.copy(.5f)
            statusCode < 300 -> successColor
            statusCode < 400 -> warningColor
            else -> errorColor
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = statusCode?.toString() ?: "---",
            style = MaterialTheme.typography.titleMedium,
        )
    }

}


@Composable
internal fun DeleteDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Delete Transactions") },
        text = { Text("Are you sure you want to delete all transactions?") },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismissRequest()
                }
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}

