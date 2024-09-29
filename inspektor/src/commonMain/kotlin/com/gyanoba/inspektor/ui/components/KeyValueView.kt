package com.gyanoba.inspektor.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun KeyValueView(
    key: String,
    value: String?,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        Text(
            text = "$key:",
            style = textStyle.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.widthIn(min = 60.dp).weight(1f)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = value ?: "",
            style = textStyle,
            modifier = Modifier.weight(3f)
        )
    }
}


@Composable
internal fun ExpandableKeyValue(
    key: String,
    value: String?,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    modifier: Modifier = Modifier,
    content: @Composable (() -> Unit)?,
) {
    var isExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.animateContentSize(),
    ) {
        Row {
            Box(Modifier.requiredSize(20.dp)) {
                if (content != null)
                    IconToggleButton(
                        checked = isExpanded,
                        onCheckedChange = { isExpanded = it },
                        content = {
                            DisableSelection {
                                Text(
                                    text = if (isExpanded) "-" else "+",
                                    style = textStyle,
                                )
                            }
                        },
                    )
            }

            KeyValueView(
                key = key,
                value = value,
                textStyle = textStyle,
                modifier = Modifier.weight(1f)
            )
        }
        if (isExpanded && content != null) {
            content()
        }
    }
}
