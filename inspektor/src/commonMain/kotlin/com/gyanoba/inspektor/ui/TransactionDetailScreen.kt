package com.gyanoba.inspektor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gyanoba.inspektor.data.InspektorDataSource
import com.gyanoba.inspektor.data.entites.HttpTransaction
import com.gyanoba.inspektor.utils.DateTimeFormatters
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.format

@Composable
internal fun TransactionDetailsScreen(transactionId: Long, onBack: () -> Unit) {
    val viewModel = viewModel {
        TransactionDetailsViewModel(transactionId)
    }
    TransactionDetailsScreen(
        viewModel.transaction.collectAsState().value,
        onBack
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionDetailsScreen(
    transaction: HttpTransaction?,
    onBack: () -> Unit
) {
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
                    Row {
                        Text(
                            text = transaction.method,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(text = transaction.path ?: "")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (transaction == null) {
            CircularProgressIndicator()
            return@Scaffold
        }
        Column(Modifier.padding(paddingValues)) {
            Card {
                Row {
                    Text(
                        text = transaction.statusCode.toString(),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Column {
                        Row {
                            Text(
                                text = transaction.method,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(text = transaction.path ?: "")
                        }
                        Text(text = transaction.host ?: "")
                        Row {
                            Text(
                                text = transaction.requestDate.format(DateTimeFormatters.simpleFormatter)
                            )
                            Text(text = transaction.tookMs?.toString() ?: "")

                        }
                    }
                }

            }
        }

    }

}

internal class TransactionDetailsViewModel(transactionId: Long) : ViewModel() {
    private val repository = InspektorDataSource.Instance
    val transaction = repository.getTransaction(transactionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
