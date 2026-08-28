package com.gyanoba.inspektor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color


internal val successColor
    @Composable @ReadOnlyComposable get() = Color(0xff4c9746)


internal val errorColor
    @Composable @ReadOnlyComposable get() = Color(0XFF992636)


internal val warningColor
    @Composable @ReadOnlyComposable get() = Color(0XFFab6426)

@Composable
internal fun InspektorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        typography = InspektorTypography,
        colorScheme = if (isSystemInDarkTheme()) darkScheme else lightScheme,
        content = content
    )
}
