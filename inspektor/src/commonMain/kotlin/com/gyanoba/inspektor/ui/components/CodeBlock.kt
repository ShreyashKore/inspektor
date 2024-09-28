package com.gyanoba.inspektor.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import com.gyanoba.inspektor.inspektor.generated.resources.Res
import com.gyanoba.inspektor.inspektor.generated.resources.round_content_copy
import com.gyanoba.inspektor.inspektor.generated.resources.round_done
import com.gyanoba.inspektor.inspektor.generated.resources.wrap_text
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
    val searchableTextState = remember {
        SearchableTextState(initialText = code, scope)
    }

    var showSearch by remember { mutableStateOf(false) }
    var softWrap by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            CopyButton(
                onClick = {
                    clipboardManager.setText(code)
                },
                contentDescription = "Copy All"
            )
            IconToggleButton(
                checked = softWrap,
                onCheckedChange = { softWrap = it },
            ) {
                Icon(
                    painterResource(Res.drawable.wrap_text),
                    contentDescription = "Wrap Text"
                )
            }
            IconToggleButton(
                checked = showSearch,
                onCheckedChange = { showSearch = it },
            ) {
                AnimatedContent(
                    targetState = showSearch,
                ) { showingSearch ->
                    if (showingSearch) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Close"
                        )
                    } else {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = "Search"
                        )
                    }
                }
            }

        }
        AnimatedVisibility(showSearch) {
            SearchTextToolbar(
                searchableTextState = searchableTextState
            )
        }

        SelectionContainer {
            SearchableText(
                searchState = searchableTextState,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
                softWrap = softWrap,
                showLineNumbers = true
            )
        }
    }
}


@Composable
internal fun CopyButton(
    onClick: () -> Unit,
    contentDescription: String = "Copy",
    modifier: Modifier = Modifier,
) {
    var showCopiedIndication by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    IconButton(
        modifier = modifier,
        onClick = {
            onClick()
            scope.launch {
                showCopiedIndication = true
                delay(1500)
                showCopiedIndication = false
            }
        },
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = if (showCopiedIndication) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            containerColor = if (showCopiedIndication) MaterialTheme.colorScheme.primary else Color.Unspecified,
        ),
    ) {
        Icon(
            painterResource(
                if (showCopiedIndication) Res.drawable.round_done else
                    Res.drawable.round_content_copy
            ),
            contentDescription = contentDescription
        )
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
