package com.gyanoba.inspektor.ui.transactiondetails.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gyanoba.inspektor.inspektor.generated.resources.Res
import com.gyanoba.inspektor.ui.components.ExpandableKeyValue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Composable
internal fun HeadersListView(
    headers: Set<Map.Entry<String, List<String>>>,
    originalHeaders: Set<Map.Entry<String, List<String>>>? = null,
): (@Composable ColumnScope.() -> Unit)? = headers.takeIf { it.isNotEmpty() }?.let {
    {
        it.forEach {
            ExpandableKeyValue(
                it.key, it.value.joinToString("; "),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                content = headersInfo[it.key.lowercase()]?.let {
                    {
                        KeyInfoAndLink(
                            it.summary,
                            "https://developer.mozilla.org/en-US/docs/${it.mdnSlug}"
                        )
                    }
                }
            )
        }
        originalHeaders?.takeIf { it.isNotEmpty() }?.let {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                HorizontalDivider(Modifier.weight(1f))
                Text(
                    "Original Headers",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(vertical = 2.dp, horizontal = 12.dp),
                    textAlign = TextAlign.Center
                )
                HorizontalDivider(Modifier.weight(1f))
            }
            it.forEach {
                ExpandableKeyValue(
                    it.key, it.value.joinToString("; "),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    content = headersInfo[it.key.lowercase()]?.let {
                        {
                            KeyInfoAndLink(
                                it.summary,
                                "https://developer.mozilla.org/en-US/docs/${it.mdnSlug}"
                            )
                        }
                    }
                )
            }
        }
    }
}

internal val headersInfo: Map<String, HeaderDoc> by lazy {
    runBlocking {
        val string = Res.readBytes("files/docs-headers.json").decodeToString()
        Json.decodeFromString<Map<String, HeaderDoc>>(string)
    }
}

@Serializable
internal data class HeaderDoc(
    val mdnSlug: String,
    val name: String,
    val summary: String,
)