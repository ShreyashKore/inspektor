package com.gyanoba.inspektor.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable
internal fun RowScope.Gap(width: Dp, modifier: Modifier = Modifier) {
    Spacer(modifier = modifier.width(width))
}

@Composable
internal fun ColumnScope.Gap(height: Dp, modifier: Modifier = Modifier) {
    Spacer(modifier = modifier.height(height))
}

@Composable
internal fun LazyItemScope.Gap(padding: Dp, modifier: Modifier = Modifier) {
    Spacer(modifier = modifier.size(padding))
}