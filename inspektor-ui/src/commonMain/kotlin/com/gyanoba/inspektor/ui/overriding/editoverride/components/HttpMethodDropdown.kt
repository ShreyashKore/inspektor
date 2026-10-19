package com.gyanoba.inspektor.ui.overriding.editoverride.components

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gyanoba.inspektor.data.HttpMethod
import com.gyanoba.inspektor.ui.components.SimpleDropdown

@Composable
internal fun HttpMethodDropdown(
    selectedMethod: HttpMethod, onMethodSelected: (HttpMethod) -> Unit
) = SimpleDropdown(
    items = HttpMethod.currentlySupported,
    selectedItem = selectedMethod,
    onItemSelected = onMethodSelected,
    itemAsString = { "HTTP ${it.name}" },
    modifier = Modifier.widthIn(max = 160.dp),
)