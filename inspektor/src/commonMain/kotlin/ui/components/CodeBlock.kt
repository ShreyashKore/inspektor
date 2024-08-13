package ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.gyanoba.inspektor.inspektor.generated.resources.Res
import com.gyanoba.inspektor.inspektor.generated.resources.round_content_copy
import com.gyanoba.inspektor.inspektor.generated.resources.round_done
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
internal fun CodeBlock(
    code: AnnotatedString,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val showCopyButton = remember { mutableStateOf(true) }
    var showCopiedIndication by remember { mutableStateOf(false) }
    val vScrollState = rememberScrollState()
    val hScrollState = rememberScrollState()
    Box(modifier = modifier) {
        SelectionContainer(
            Modifier
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        showCopyButton.value = false
                        do {
                            val event = awaitPointerEvent()
                        } while (event.changes.any { it.pressed })
                        showCopyButton.value = true
                    }
                }
        ) {
            Text(
                code,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                softWrap = true,
                modifier = Modifier.fillMaxSize().padding(8.dp)
                    .verticalScrollbar(vScrollState)
                    .horizontalScrollbar(hScrollState)
                    .verticalScroll(vScrollState)
                    .horizontalScroll(hScrollState)
            )
        }


        AnimatedVisibility(
            true,
            enter = fadeIn(tween(delayMillis = 1000)),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
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
            ) {
                Icon(
                    painterResource(
                        if (showCopiedIndication) Res.drawable.round_done else
                            Res.drawable.round_content_copy
                    ),
                    contentDescription = "Copy All"
                )
            }
        }
    }
}

@Preview
@Composable
private fun CodeBlockPreview() {
    CodeBlock(
        AnnotatedString(SAMPLE_JSON)
    )
}

private const val SAMPLE_JSON = """
{
    "glossary": {
        "title": "example glossary",
		"GlossDiv": {
            "title": "S",
			"GlossList": {
                "GlossEntry": {
                    "ID": "SGML",
					"SortAs": "SGML",
					"GlossTerm": "Standard Generalized Markup Language",
					"Acronym": "SGML",
					"Abbrev": "ISO 8879:1986",
					"GlossDef": {
                        "para": "A meta-markup language, used to create markup languages such as DocBook.",
						"GlossSeeAlso": ["GML", "XML"]
                    },
					"GlossSee": "markup"
                }
            }
        }
    }
}
"""
