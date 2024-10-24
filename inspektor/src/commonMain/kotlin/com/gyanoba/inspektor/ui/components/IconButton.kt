package com.gyanoba.inspektor.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
internal fun DefaultIconButton(
    onClick: () -> Unit,
    tooltipText: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    SimpleTooltip(
        tooltipText = tooltipText,
        content = {
            IconButton(
                onClick = onClick,
                modifier = modifier,
                content = content,
            )
        }
    )
}

@Composable
internal fun DefaultIconButton(
    onClick: () -> Unit,
    tooltipText: String,
    modifier: Modifier = Modifier,
    icon: ImageVector,
) {
    DefaultIconButton(
        onClick = onClick,
        tooltipText = tooltipText,
        modifier = modifier,
    ) {
        Icon(icon, contentDescription = tooltipText)
    }
}
