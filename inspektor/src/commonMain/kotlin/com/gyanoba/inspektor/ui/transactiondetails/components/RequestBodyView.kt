package com.gyanoba.inspektor.ui.transactiondetails.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import com.gyanoba.inspektor.data.HttpTransaction
import com.gyanoba.inspektor.ui.components.CodeBlock
import com.gyanoba.inspektor.ui.components.Format


@Composable
internal fun RequestBodyView(transaction: HttpTransaction) = Column {
    if (transaction.originalRequestBody != null) {
        SimpleAccordion(
            title = "Original Request Body",
            initialExpanded = false
        ) {
            CodeBlock(
                AnnotatedString(transaction.originalRequestBody!!),
                Modifier.fillMaxWidth(),
                format = Format.parse(transaction.requestContentType),
            )
        }
    }
    if (transaction.requestBody.isNullOrEmpty()) {
        EmptyBody()
        return
    }
    CodeBlock(
        AnnotatedString(transaction.requestBody!!),
        Modifier.fillMaxWidth(),
        format = Format.parse(transaction.requestContentType),
    )
}