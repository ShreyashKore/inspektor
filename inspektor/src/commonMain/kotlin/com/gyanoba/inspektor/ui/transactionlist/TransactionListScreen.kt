package com.gyanoba.inspektor.ui.transactionlist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gyanoba.inspektor.data.GetAllLatestWithLimit
import com.gyanoba.inspektor.data.InspektorDataSourceImpl
import com.gyanoba.inspektor.platform.FileSharer
import com.gyanoba.inspektor.platform.getAppName
import com.gyanoba.inspektor.ui.UiEvent
import com.gyanoba.inspektor.ui.components.DateRangeButton
import com.gyanoba.inspektor.ui.components.DateRangePickerDialog
import com.gyanoba.inspektor.ui.components.Logo
import com.gyanoba.inspektor.ui.components.SimpleSearchBar
import com.gyanoba.inspektor.ui.transactionlist.components.DeleteDialog
import com.gyanoba.inspektor.ui.transactionlist.components.TransactionItem
import com.gyanoba.inspektor.utils.DateFormatters
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime

@Composable
internal fun TransactionListScreen(
    openTransaction: (Long) -> Unit,
    openOverridesScreen: () -> Unit,
    openAddOverrideScreen: (Long) -> Unit,
) {
    val viewModel = viewModel<TransactionListViewModel> {
        TransactionListViewModel(InspektorDataSourceImpl.Instance, FileSharer())
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var alertDialogData by remember { mutableStateOf<UiEvent.ShowErrorDialog?>(null) }
    if (alertDialogData != null) {
        AlertDialog(
            onDismissRequest = { alertDialogData = null },
            title = { Text("Error") },
            text = { Text(alertDialogData?.message ?: "") },
            confirmButton = {
                TextButton(onClick = { alertDialogData = null }) {
                    Text("OK")
                }
            }
        )
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.ShowSnackBar -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                    )
                }
                is UiEvent.ShowErrorDialog -> {
                    alertDialogData = event
                }
                else -> {}
            }
        }
    }

    TransactionListScreen(
        viewModel.transactions.collectAsState().value,
        viewModel.searchFieldState,
        openTransaction,
        openOverridesScreen,
        openAddOverrideScreen,
        viewModel.allCount.collectAsState().value,
        viewModel.startDate.collectAsState().value,
        viewModel.endDate.collectAsState().value,
        viewModel::onDateRangeSelected,
        viewModel::deleteTransactions,
        viewModel::shareAsHar,
        snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun TransactionListScreen(
    transactions: List<GetAllLatestWithLimit> = emptyList(),
    searchTermState: TextFieldState,
    onClickTransaction: (Long) -> Unit,
    openOverridesScreen: () -> Unit,
    onAddOverride: (transaction: Long) -> Unit,
    allCount: Long,
    startDate: Instant,
    endDate: Instant,
    onDateRangeSelected: (Instant, Instant) -> Unit,
    onDeleteTransactions: (Instant) -> Unit,
    onShareAsHar: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    var showDateRangePicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    var showMenu by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

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
                onDeleteTransactions(it)
                showDeleteDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = { Logo() },
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
                    IconButton(onClick = { showSearch = !showSearch }) {
                        AnimatedContent(
                            targetState = showSearch,
                        ) { showSearch ->
                            if (showSearch) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Close search"
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.Search,

                                    contentDescription = "Search"
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier) {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More options"
                            )
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    showDeleteDialog = true
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("View Overrides") },
                                onClick = {
                                    openOverridesScreen()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export As HAR") },
                                onClick = {
                                    onShareAsHar()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(16.dp)
            )
        },
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues)) {
            AnimatedVisibility(visible = showSearch) {
                SimpleSearchBar(
                    searchFieldState = searchTermState,
                    placeholder = { Text("Search by status code or path") },
                )
            }
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
                transactions.groupBy { it.requestDate?.toLocalDateTime(TimeZone.currentSystemDefault())?.date }
                    .map { (date, transactions) ->
                        stickyHeader(key = date.toString()) {
                            Text(
                                date?.format(DateFormatters.simpleLocalFormatter) ?: "Unknown",
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface).padding(4.dp)
                            )
                        }
                        items(transactions, key = { it.id }) { transaction ->
                            TransactionItem(
                                transaction = transaction,
                                onClick = { onClickTransaction(transaction.id) },
                                onAddOverride = { onAddOverride(transaction.id) },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
            }
        }

    }
}
