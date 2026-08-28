package com.gyanoba.inspektor.ui.overriding.editoverride.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gyanoba.inspektor.ui.components.Gap
import com.gyanoba.inspektor.ui.components.SimpleTextField

@Composable
internal fun StatusRequestResponseEdit(
    value: StatusRequestResponse,
    showStatusCode: Boolean,
    updateValue: (StatusRequestResponse) -> Unit,
) = Column {
    if (false) { // Disable for now
        SimpleTextField(
            value = "${value.statusCode}",
            onValueChange = { updateValue(value.copy(statusCode = it.toIntOrNull())) },
            placeholder = "Status Code",
            modifier = Modifier.fillMaxWidth(),
        )
        Gap(8.dp)
    }
    Text(
        "Headers",
        style = MaterialTheme.typography.labelMedium.copy(
            color = MaterialTheme.colorScheme.onSurface.copy(.6f)
        ),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Start,
    )
    Gap(8.dp)
    value.headers.forEach {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text(
                it.key, Modifier.weight(.3f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(.6f)
            )
            Gap(8.dp)
            Text(
                it.value.joinToString(";"),
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                modifier = Modifier.weight(.7f),
            )
            IconButton(
                modifier = Modifier.size(22.dp).padding(2.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface.copy(.6f)
                ),
                onClick = {
                    val headers = value.headers.toMutableMap()
                    headers.remove(it.key)
                    updateValue(value.copy(headers = headers))
                }
            ) {
                Icon(Icons.Default.Clear, contentDescription = "Remove Header")
            }
        }
    }
    NewHeader(
        onAddHeader = {
            val headers = value.headers.toMutableMap()
            headers[it.name] = it.value.split(";")
            updateValue(value.copy(headers = headers))
        },
    )
    Gap(8.dp)
    OutlinedTextField(
        value = value.body ?: "",
        onValueChange = {
            updateValue(value.copy(body = it))
        },
        label = { Text("Body") },
        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
        shape = RoundedCornerShape(8.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
    )
}

internal data class StatusRequestResponse(
    val headers: Map<String, List<String>>,
    val body: String?,
    val statusCode: Int?,
)


@Composable
internal fun NewHeader(onAddHeader: (NewHeader) -> Unit) = Row(
    Modifier.clip(
        RoundedCornerShape(12.dp)
    ).background(
        MaterialTheme.colorScheme.surface.copy(.5f)
    ),
    verticalAlignment = Alignment.CenterVertically,
) {
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun validateAndAddHeader() {
        error = validateHeader(name, value)
        if (error != null) return

        onAddHeader(NewHeader(name, value))
        name = ""
        value = ""
    }

    Column(
        Modifier.weight(1f).padding(8.dp)
    ) {
        SimpleTextField(
            value = name, onValueChange = { name = it },
            placeholder = "Header Name",
            modifier = Modifier.fillMaxWidth(),
        )
        Gap(8.dp)
        SimpleTextField(
            value = value,
            onValueChange = { value = it },
            placeholder = "Header Value",
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )
        error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
    IconButton(
        onClick = { validateAndAddHeader() },
        enabled = name.isNotBlank() && value.isNotBlank()
    ) {
        Icon(Icons.Default.Add, contentDescription = "Add")
    }
}

internal data class NewHeader(val name: String, val value: String)

internal fun validateHeader(name: String, value: String): String? {
    if (name.isBlank()) return "Header name should not be empty"
    if (value.isBlank()) return "Header value should not be empty"
    if (name.contains(" ")) return "Header name should not contain spaces"
    if (name.contains(":")) return "Header name should not contain ':'"
    if (value.contains("\n")) return "Header value should not contain new lines"
    return null
}