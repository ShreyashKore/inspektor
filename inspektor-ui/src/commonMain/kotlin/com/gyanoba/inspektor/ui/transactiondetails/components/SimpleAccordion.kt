package com.gyanoba.inspektor.ui.transactiondetails.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gyanoba.inspektor.ui.components.Accordion

@Composable
internal fun SimpleAccordion(
    title: String,
    modifier: Modifier = Modifier,
    initialExpanded: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Accordion(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp).fillMaxWidth()
            )
        },
        initialExpanded = initialExpanded,
        modifier = modifier.padding(8.dp, 4.dp),
    ) {
        content?.let {
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                SelectionContainer {
                    Column {
                        content()
                    }
                }
            }
        }
    }
}