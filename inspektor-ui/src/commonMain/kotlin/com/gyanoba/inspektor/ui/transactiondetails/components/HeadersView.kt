package com.gyanoba.inspektor.ui.transactiondetails.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.gyanoba.inspektor.data.HttpTransaction
import com.gyanoba.inspektor.ui.components.KeyValueView


@Composable
internal fun HeadersView(transaction: HttpTransaction) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.verticalScroll(scrollState)
    ) {
        SimpleAccordion(
            title = "Overview",
            initialExpanded = true
        ) {
            val valueTextStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            KeyValueView("URL", transaction.url, textStyle = valueTextStyle)
            KeyValueView("Method", transaction.method, textStyle = valueTextStyle)
            KeyValueView("Response Code", transaction.responseCode?.toString(), textStyle = valueTextStyle)
            KeyValueView("Host", transaction.host, textStyle = valueTextStyle)
            if (transaction.error != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = transaction.error ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
        val requestHeaders = transaction.requestHeaders ?: emptySet()
        SimpleAccordion(
            title = "Request Headers (${requestHeaders.size})",
            initialExpanded = true,
            content = HeadersListView(
                requestHeaders,
                transaction.originalRequestHeaders,
            )
        )
        val responseHeaders = transaction.responseHeaders ?: emptySet()
        SimpleAccordion(
            title = "Response Headers (${responseHeaders.size})",
            initialExpanded = true,
            content = HeadersListView(
                responseHeaders,
                transaction.originalResponseHeaders,
            )
        )
    }
}

