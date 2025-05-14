package com.gyanoba.inspektor.ui.transactiondetails.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import com.gyanoba.inspektor.data.HttpTransaction
import com.gyanoba.inspektor.ui.components.CodeBlock
import com.gyanoba.inspektor.ui.components.Format


@Composable
internal fun ResponseBodyView(transaction: HttpTransaction) {
    if (transaction.originalResponseBody != null) {
        SimpleAccordion(
            title = "Original Response Body",
            initialExpanded = false
        ) {
            CodeBlock(
                AnnotatedString(transaction.originalResponseBody!!),
                Modifier.fillMaxWidth(),
                format = Format.parse(transaction.responseContentType),
            )
        }
    }
    if (transaction.responseBody.isNullOrEmpty()) {
        EmptyBody()
        return
    }
    CodeBlock(
        AnnotatedString(transaction.responseBody!!),
        Modifier.fillMaxWidth(),
        format = Format.parse(transaction.responseContentType),
    )
}