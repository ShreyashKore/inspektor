package ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import com.gyanoba.inspektor.inspektor.generated.resources.Res
import com.gyanoba.inspektor.inspektor.generated.resources.round_content_copy
import com.gyanoba.inspektor.inspektor.generated.resources.round_done
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun CodeBlock(
    code: AnnotatedString,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var showCopiedIndication by remember { mutableStateOf(false) }
    val vScrollState = rememberScrollState()
    val hScrollState = rememberScrollState()
    Box(modifier = modifier) {
        SelectionContainer {
            Text(
                code,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxSize().verticalScroll(vScrollState)
                    .horizontalScroll(hScrollState)
            )
        }
        IconButton(
            onClick = {
                clipboardManager.setText(code)
                scope.launch {
                    showCopiedIndication = true
                    delay(1500)
                    showCopiedIndication = false
                }
            },
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = if (showCopiedIndication) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                containerColor = if (showCopiedIndication) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            ),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Icon(
                painterResource(
                    if (showCopiedIndication) Res.drawable.round_done else
                        Res.drawable.round_content_copy
                ),
                contentDescription = "Copy"
            )
        }
    }

}
