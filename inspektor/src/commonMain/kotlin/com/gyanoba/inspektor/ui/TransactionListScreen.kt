package com.gyanoba.inspektor.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gyanoba.inspektor.data.GetAllLatestWithLimit
import com.gyanoba.inspektor.data.InspektorDataSourceImpl
import com.gyanoba.inspektor.platform.getAppName
import com.gyanoba.inspektor.ui.components.AddOverrideIcon
import com.gyanoba.inspektor.ui.components.DateRangeButton
import com.gyanoba.inspektor.ui.components.DateRangePickerDialog
import com.gyanoba.inspektor.ui.components.DefaultIconButton
import com.gyanoba.inspektor.ui.components.Logo
import com.gyanoba.inspektor.ui.components.SimpleSearchBar
import com.gyanoba.inspektor.ui.theme.errorColor
import com.gyanoba.inspektor.ui.theme.successColor
import com.gyanoba.inspektor.ui.theme.warningColor
import com.gyanoba.inspektor.utils.DateFormatters
import com.gyanoba.inspektor.utils.TimeFormatters
import com.gyanoba.inspektor.utils.atLocalStartOfDay
import kotlinx.datetime.Clock
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
        TransactionListViewModel(InspektorDataSourceImpl.Instance)
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
        viewModel::deleteTransactions
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
                                text = { Text("Overrides") },
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
                        }
                    }
                },
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
                        stickyHeader(key = date) {
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

@Composable
internal fun TransactionItem(
    transaction: GetAllLatestWithLimit,
    onClick: () -> Unit,
    onAddOverride: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            StatusCodeView(
                transaction.responseCode,
                isOverridden = transaction.isOverridden,
                modifier = Modifier.width(60.dp),
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
            DefaultIconButton(
                onClick = onAddOverride,
                tooltipText = "Add Override",
            ) { AddOverrideIcon() }
        }
        if (transaction.error != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = transaction.error ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun StatusCodeView(
    statusCode: Long?,
    isOverridden: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier,
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
        if (isOverridden) {
            Spacer(Modifier.height(10.dp))
            Icon(
                Icons.Rounded.Edit,
                contentDescription = "Overridden",
                tint = MaterialTheme.colorScheme.primary.copy(.6f),
                modifier = Modifier.size(18.dp).background(
                    MaterialTheme.colorScheme.primary.copy(.2f),
                    CircleShape
                ).padding(4.dp)
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeleteDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (Instant) -> Unit,
) {
    val datePickerState = rememberDatePickerState(initialDisplayMode = DisplayMode.Input)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Delete Transactions") },
        text = {
            Column {
                Text("Are you sure you want to delete all transactions ${
                    datePickerState.selectedDateInstant?.atLocalStartOfDay(TimeZone.currentSystemDefault())
                        ?.toLocalDateTime(TimeZone.currentSystemDefault())?.date
                        ?.format(DateFormatters.simpleLocalFormatter)?.let { "before $it" } ?: ""
                }?")
                DatePicker(
                    title = null,
                    headline = null,
                    showModeToggle = false,
                    state = datePickerState,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        datePickerState.selectedDateInstant
                            ?.atLocalStartOfDay(TimeZone.currentSystemDefault())
                            ?: Clock.System.now()
                    )
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

@OptIn(ExperimentalMaterial3Api::class)
internal val DatePickerState.selectedDateInstant: Instant?
    get() = selectedDateMillis?.let { Instant.fromEpochMilliseconds(it) }


private val GetAllLatestWithLimit.isOverridden: Boolean
    get() = isOverriddenNum > 0
