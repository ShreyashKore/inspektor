package ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.graphics.Color


@OptIn(InternalComposeApi::class)
val successColor
    @Composable get() = when (LocalSystemTheme.current) {
        SystemTheme.Light -> Color(0XFF37bf2a)
        SystemTheme.Dark -> Color(0XFF65ed58)
        else -> Color(0XFF37bf2a)
    }


@OptIn(InternalComposeApi::class)
val errorColor
    @Composable get() = when (LocalSystemTheme.current) {
        SystemTheme.Light -> Color(0XFF992636)
        SystemTheme.Dark -> Color(0xFFd65869)
        else -> Color(0XFF992636)
    }


@OptIn(InternalComposeApi::class)
val warningColor
    @Composable get() = when (LocalSystemTheme.current) {
        SystemTheme.Light -> Color(0XFFab6426)
        SystemTheme.Dark -> Color(0XFFd69358)
        else -> Color(0XFFab6426)
    }