package com.gyanoba.inspektor.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gyanoba.inspektor.inspektor.generated.resources.Res
import com.gyanoba.inspektor.inspektor.generated.resources.inspektor
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun Logo(modifier: Modifier = Modifier.size(36.dp)) {
    Image(
        painter = painterResource(Res.drawable.inspektor),
        contentDescription = "Inspektor Logo",
        modifier = modifier
    )
}