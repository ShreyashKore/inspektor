package com.gyanoba.inspektor.ui.components


import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SimpleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    colors: TextFieldColors = TextFieldDefaults.colors(),
    contentPadding: PaddingValues = PaddingValues(vertical = 2.dp, horizontal = 4.dp),
    visualTransformation: VisualTransformation = VisualTransformation.None,
    modifier: Modifier = Modifier.width(500.dp),
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(modifier) {
        label?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            interactionSource = interactionSource,
            enabled = enabled,
            singleLine = singleLine,
        ) { innerTextField ->
            TextFieldDefaults.DecorationBox(
                visualTransformation = visualTransformation,
                value = value,
                innerTextField = innerTextField,
                singleLine = singleLine,
                enabled = enabled,
                label = label?.let { { Text(it) } },
                placeholder = placeholder?.let { { Text(placeholder) } },
                colors = colors,
                shape = RoundedCornerShape(8.dp),
                interactionSource = interactionSource,
                contentPadding = contentPadding
            )
        }
    }
}

@Composable
private fun SimpleTextFieldPreview() {
    val textState = remember { mutableStateOf("") }
    SimpleTextField(
        value = textState.value,
        onValueChange = { textState.value = it },
        label = "Label",
        placeholder = "Placeholder",
    )
}
