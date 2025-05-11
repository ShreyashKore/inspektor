package com.gyanoba.inspektor.ui.transactionlist.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gyanoba.inspektor.data.GetAllLatestWithLimit
import com.gyanoba.inspektor.ui.components.AddOverrideIcon
import com.gyanoba.inspektor.ui.components.DefaultIconButton
import com.gyanoba.inspektor.utils.TimeFormatters
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime

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


private val GetAllLatestWithLimit.isOverridden: Boolean
    get() = isOverriddenNum > 0
