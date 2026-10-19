package com.gyanoba.inspektor.ui.transactiondetails.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp


@Composable
internal fun KeyInfoAndLink(
    info: String,
    link: String,
    modifier: Modifier = Modifier.padding(bottom = 8.dp, top = 4.dp),
) = Column(modifier = modifier) {
    Text(
        text = buildAnnotatedString {
            append(info)
            append(" ")
            withStyle(
                SpanStyle(
                    color =  MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline
                )
            ) {
                withLink(LinkAnnotation.Url(link)) {
                    append("More info ↗")
                }
            }
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    )
}