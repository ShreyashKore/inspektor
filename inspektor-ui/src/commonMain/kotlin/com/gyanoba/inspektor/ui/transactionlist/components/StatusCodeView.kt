package com.gyanoba.inspektor.ui.transactionlist.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.gyanoba.inspektor.ui.theme.errorColor
import com.gyanoba.inspektor.ui.theme.successColor
import com.gyanoba.inspektor.ui.theme.warningColor


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