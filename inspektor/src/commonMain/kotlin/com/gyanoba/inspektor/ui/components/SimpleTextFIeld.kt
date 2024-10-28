package com.gyanoba.inspektor.ui.components


import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
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
    isError: Boolean = false,
    singleLine: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    ),
    contentPadding: PaddingValues = PaddingValues(vertical = 4.dp, horizontal = 10.dp),
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
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
            textStyle = textStyle,
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
        ) { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                visualTransformation = visualTransformation,
                value = value,
                innerTextField = innerTextField,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled,
                        isError = isError,
                        interactionSource = interactionSource,
                        colors = colors,
                        shape = RoundedCornerShape(8.dp),
                    )
                },
                singleLine = singleLine,
                enabled = enabled,
                label = label?.let { { Text(it) } },
                placeholder = placeholder?.let { { Text(placeholder) } },
                colors = colors,
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
