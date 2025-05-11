package com.gyanoba.inspektor.ui.transactiondetails

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gyanoba.inspektor.data.HttpTransaction
import com.gyanoba.inspektor.data.InspektorDataSourceImpl
import com.gyanoba.inspektor.ui.components.AddOverrideIcon
import com.gyanoba.inspektor.ui.components.DefaultIconButton
import com.gyanoba.inspektor.ui.transactiondetails.components.HeadersView
import com.gyanoba.inspektor.ui.transactiondetails.components.RequestBodyView
import com.gyanoba.inspektor.ui.transactiondetails.components.ResponseBodyView
import com.gyanoba.inspektor.utils.toCurlString

@Composable
internal fun TransactionDetailsScreen(
    transactionId: Long,
    onBack: () -> Unit,
    openAddOverrideScreen: () -> Unit,
) {
    val viewModel = viewModel {
        TransactionDetailsViewModel(transactionId, InspektorDataSourceImpl.Instance)
    }
    TransactionDetailsScreen(
        viewModel.transaction.collectAsState().value, onBack, openAddOverrideScreen
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionDetailsScreen(
    transaction: HttpTransaction?,
    onBack: () -> Unit,
    openAddOverrideScreen: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    if (transaction == null) {
                        Text(text = "Loading...")
                        return@CenterAlignedTopAppBar
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = transaction.method ?: "",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = transaction.path ?: "",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                },
                actions = {
                    DefaultIconButton(
                        onClick = {
                            // Copy to clipboard
                            clipboardManager.setText(
                                AnnotatedString(
                                    transaction?.toCurlString() ?: ""
                                )
                            )
                        },
                        tooltipText = "Copy as cURL",
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Copy as cURL")
                    }
                    DefaultIconButton(
                        onClick = openAddOverrideScreen,
                        tooltipText = "Add Override",
                    ) { AddOverrideIcon() }
                },
            )
        },
    ) { paddingValues ->
        if (transaction == null) {
            CircularProgressIndicator()
            return@Scaffold
        }

        var selectedTabIndex by remember { mutableStateOf(0) }

        Column(Modifier.padding(paddingValues)) {
            PrimaryTabRow(
                selectedTabIndex = 0,
                indicator = {
                    TabRowDefaults.PrimaryIndicator(
                        Modifier.tabIndicatorOffset(selectedTabIndex),
                        color = MaterialTheme.colorScheme.primary
                    )
                },
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Headers") },
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        Text(
                            "Request" + transaction.requestPayloadSize?.let { " ($it)" }.orEmpty()
                        )
                    },
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = {
                        Text(
                            "Response" + transaction.responsePayloadSize?.let { " ($it)" }.orEmpty()
                        )
                    },
                )
            }

            when (selectedTabIndex) {
                0 -> HeadersView(transaction)
                1 -> RequestBodyView(transaction)
                2 -> ResponseBodyView(transaction)
            }

        }

    }

}