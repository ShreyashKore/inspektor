package com.gyanoba.inspektor.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


@Composable
internal fun AddOverrideIcon(
    modifier: Modifier = Modifier.size(24.dp)
) {
    Box(
        modifier = modifier
    ) {
        Icon(
            Icons.Default.Edit,
            contentDescription = "Add Override",
            modifier = Modifier.padding(bottom = 8.dp).size(22.dp)
        )
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(16.dp).align(Alignment.BottomEnd)
        )
    }
}