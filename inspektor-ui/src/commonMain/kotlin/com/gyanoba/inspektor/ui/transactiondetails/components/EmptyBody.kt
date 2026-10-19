package com.gyanoba.inspektor.ui.transactiondetails.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
internal fun EmptyBody() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No body")
    }
}