package ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun KeyValueView(
    key: String,
    value: String?,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        Text(
            text = "$key:",
            style = textStyle.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.widthIn(min = 60.dp).weight(1f)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = value ?: "",
            style = textStyle,
            modifier = Modifier.weight(3f)
        )
    }
}
