package com.gyanoba.inspektor.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gyanoba.inspektor.utils.log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SearchableText(
    searchState: SearchableTextState,
    style: TextStyle = LocalTextStyle.current,
    softWrap: Boolean = true,
    showLineNumbers: Boolean = false,
    modifier: Modifier = Modifier,
) {

    val verticalScrollState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()

    val activeResult = searchState.focusedSearchResult

    LaunchedEffect(activeResult) {
        if (activeResult == null) return@LaunchedEffect
        if (activeResult in searchState.searchResults) {
            verticalScrollState.animateScrollToItem(activeResult.lineIndex)
        }
    }

    LazyColumn(
        state = verticalScrollState,
        modifier = modifier.run {
            if (softWrap) this
            else this.horizontalScrollbar(horizontalScrollState)
                .horizontalScroll(horizontalScrollState)
        },
    ) {
        val aLargeNumber = "${searchState.lines.size * 10}"
        itemsIndexed(searchState.lines) { lineIndex, line ->
            val ranges = searchState.searchResults.getAllForLine(lineIndex).map { it.range }
            val activeRangeForCurLine = searchState.focusedSearchResult?.range?.takeIf {
                searchState.focusedSearchResult?.lineIndex == lineIndex
            }
            val styledText = remember(line, ranges, activeRangeForCurLine) {
                buildStyledString(line, ranges, activeRangeForCurLine)
            }

            Row {
                if (showLineNumbers) {
                    DisableSelection {
                        Box(
                            Modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            /// Invisible text to reserve space for line numbers
                            Text(
                                text = aLargeNumber,
                                style = style,
                                modifier = Modifier.alpha(0f).padding(horizontal = 4.dp),
                            )
                            Text(
                                text = "${lineIndex + 1}",
                                style = style.copy(color = style.color.copy(alpha = 0.5f)),
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                }
                Text(
                    text = styledText,
                    modifier = Modifier,
                    style = style,
                    softWrap = softWrap,
                )
            }
        }
    }
}


private fun List<CharSequence>.getSearchResults(
    regex: Regex,
): SearchResults {
    if (regex.toString().isEmpty()) {
        return SearchResults(emptyList(), emptyMap())
    }

    val lineNoToSearchResults = mutableMapOf<Int, List<SearchResult>>()
    val allSearchResults = flatMapIndexed { lineIndex, line ->
        val lineResults = line.getSubstringRanges(regex).map {
            val result = SearchResult(lineIndex, it)
            result
        }
        lineNoToSearchResults[lineIndex] = lineResults
        lineResults
    }
    return SearchResults(allSearchResults, lineNoToSearchResults)
}


private fun CharSequence.getSubstringRanges(regex: Regex) =
    regex.findAll(this).map { it.range }.toList()

/**
 * State for a text that can be searched.
 * @param initialText The initial text to search.
 * @param initialSearchText The initial search text.
 * @param initialSearchOptions The initial search options.
 */
@Stable
internal class SearchableTextState(
    initialText: AnnotatedString,
    private val scope: CoroutineScope,
    initialSearchText: String? = null,
    initialSearchOptions: Set<SearchOptions> = emptySet(),
) {
    var text by mutableStateOf(initialText)
        private set
    var options by mutableStateOf(initialSearchOptions)
        private set
    val lines by derivedStateOf {
        text.split('\n')
    }
    var searchText by mutableStateOf(initialSearchText)
        private set
    var focusedSearchResultIndex by mutableStateOf(0)
        private set
    var regexError by mutableStateOf<Exception?>(null)
        private set
    val isRegexValid get() = regexError == null

    private var job: Job? = null

    var searchResults by mutableStateOf(SearchResults(emptyList(), emptyMap()))
        private set

    var isLoading by mutableStateOf(false)
        private set

    private fun calculateSearchResults() {
        job?.cancel()
        job = scope.launch {
            isLoading = true
            searchResults = getSearchResults().also { println("SearchResults ${it.size}") }
        }.apply {
            invokeOnCompletion {
                if (it !is CancellationException) isLoading = false
            }
        }
    }

    private suspend fun getSearchResults(): SearchResults {
        val searchText = this.searchText
        if (searchText.isNullOrEmpty())
            return SearchResults(emptyList(), emptyMap())

        val options = this.options
        val escapedText =
            if (SearchOptions.REGEX in options) searchText else searchText.regexMetaCharsEscaped()
        val regexOptions =
            if (SearchOptions.CASE_SENSITIVE !in options) setOf(RegexOption.IGNORE_CASE)
            else emptySet()

        val searchRegex = try {
            regexError = null
            escapedText.toRegex(regexOptions)
        } catch (e: Exception) {
            regexError = e
            return SearchResults(emptyList(), emptyMap())
        }
        return withContext(Dispatchers.Default) {
            lines.getSearchResults(searchRegex)
        }
    }

    val focusedSearchResult get() = searchResults.getOrNull(focusedSearchResultIndex)


    fun updateFocusedSearchResult(index: Int) {
        if (index !in searchResults.indices) {
            log("SearchableText") { "Invalid index $index, searchResults size: ${searchResults.size}" }
            return
        }
        focusedSearchResultIndex = index
    }

    fun updateSearchText(text: String) {
        searchText = text
        focusedSearchResultIndex = 0
        calculateSearchResults()
    }

    fun updateOptions(options: Set<SearchOptions>) {
        this.options = options
        focusedSearchResultIndex = 0
        calculateSearchResults()
    }
}


internal enum class SearchOptions {
    CASE_SENSITIVE, REGEX,
}

internal fun SearchableTextState.previousSearchResult(loopToLast: Boolean = true) {
    val newIndex = if (focusedSearchResultIndex == 0) {
        if (loopToLast) searchResults.lastIndex else 0
    } else {
        focusedSearchResultIndex - 1
    }
    updateFocusedSearchResult(newIndex)
}

internal fun SearchableTextState.nextSearchResult(loopToFirst: Boolean = true) {
    val newIndex = if (focusedSearchResultIndex == searchResults.lastIndex) {
        if (loopToFirst) 0 else searchResults.lastIndex
    } else {
        focusedSearchResultIndex + 1
    }
    updateFocusedSearchResult(newIndex)
}


@Immutable
internal class SearchResults(
    private val results: List<SearchResult>,
    private val linesToResults: Map<Int, List<SearchResult>>,
) : List<SearchResult> by results {
    fun getOrNull(index: Int): SearchResult? = results.getOrNull(index)
    fun getAllForLine(index: Int): List<SearchResult> = linesToResults[index] ?: emptyList()

    override fun toString(): String {
        return "${super.toString()}(results=${results.size})"
    }
}

internal data class SearchResult(
    val lineIndex: Int,
    val range: IntRange,
)

private fun buildStyledString(
    line: String,
    ranges: List<IntRange>,
    activeRange: IntRange?,
) = buildAnnotatedString {
    append(line)
    if (ranges.isNotEmpty()) {
        for (range in ranges) {
            addStyle(
                style = SpanStyle(background = Color.Yellow.copy(alpha = 0.3f)),
                start = range.first,
                end = range.last + 1
            )
        }

        if (activeRange != null) {
            addStyle(
                style = SpanStyle(background = Color.Blue.copy(alpha = 0.3f)),
                start = activeRange.first,
                end = activeRange.last + 1
            )
        }
    }
}

internal val RegexMetaCharacters = setOf(
    "\\", "^", "$", ".", "|", "?", "*", "+", "(", ")", "[", "]", "{", "}"
)

internal fun String.regexMetaCharsEscaped(): String {
    var escapedString = this
    for (char in RegexMetaCharacters) {
        escapedString = escapedString.replace(char, "\\$char")
    }
    return escapedString
}


@Composable
internal fun SearchTextToolbar(
    searchableTextState: SearchableTextState,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
) = SearchTextToolbar(
    searchText = searchableTextState.searchText ?: "",
    onSearchTextChange = searchableTextState::updateSearchText,
    foundCount = searchableTextState.searchResults.size,
    currentActive = searchableTextState.focusedSearchResultIndex + 1,
    currentOptions = searchableTextState.options,
    onChangeOptions = searchableTextState::updateOptions,
    onPrevious = searchableTextState::previousSearchResult,
    onNext = searchableTextState::nextSearchResult,
    onDismiss = onDismiss,
    isRegexValid = searchableTextState.isRegexValid,
    isLoading = searchableTextState.isLoading,
    modifier = modifier
)

@Composable
internal fun SearchTextToolbar(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    foundCount: Int,
    currentActive: Int,
    currentOptions: Set<SearchOptions>,
    onChangeOptions: (Set<SearchOptions>) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
    onClear: () -> Unit = { onSearchTextChange("") },
    onDismiss: (() -> Unit)? = null,
    singleLine: Boolean = true,
    isRegexValid: Boolean = true,
    isLoading: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(modifier = modifier.fillMaxWidth().padding(8.dp)) {

        OutlinedTextField(
            value = searchText,
            isError = !isRegexValid,
            onValueChange = {
                onSearchTextChange(it)
            },
            textStyle = MaterialTheme.typography.bodyMedium.run {
                copy(color = if (!isRegexValid) MaterialTheme.colorScheme.error else color)
            },
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
            shape = MaterialTheme.shapes.medium.copy(CornerSize(16.dp)),
            trailingIcon = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    if (searchText.isNotEmpty()) {
                        IconButton(
                            onClick = onClear,
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Clear,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                contentDescription = "Clear",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    SearchOptionButton(
                        onChecked = { isChecked ->
                            val newOptions = if (isChecked) {
                                currentOptions + SearchOptions.CASE_SENSITIVE
                            } else {
                                currentOptions - SearchOptions.CASE_SENSITIVE
                            }
                            onChangeOptions(newOptions)
                        },
                        contentDescription = "Case Sensitive",
                        checked = currentOptions.contains(SearchOptions.CASE_SENSITIVE),
                        icon = "Aa",
                    )
                    SearchOptionButton(
                        onChecked = { isChecked ->
                            val newOptions = if (isChecked) {
                                currentOptions + SearchOptions.REGEX
                            } else {
                                currentOptions - SearchOptions.REGEX
                            }
                            onChangeOptions(newOptions)
                        },
                        contentDescription = "Regex",
                        checked = currentOptions.contains(SearchOptions.REGEX),
                        icon = ".*",
                    )
                }
            },
            singleLine = singleLine,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        )

        Spacer(Modifier.size(4.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            val text = when {
                !isRegexValid -> "Invalid Regex"
                isLoading -> "Searching..."
                foundCount == 0 -> "No Results"
                else -> "$currentActive/$foundCount"
            }
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.weight(1f))
            SearchActionButton(
                onClick = onPrevious,
                contentDescription = "Previous",
                icon = Icons.Rounded.KeyboardArrowUp,
                enabled = foundCount > 0
            )
            SearchActionButton(
                onClick = onNext,
                contentDescription = "Next",
                icon = Icons.Rounded.KeyboardArrowDown,
                enabled = foundCount > 0
            )
            if (onDismiss != null)
                SearchActionButton(
                    onClick = onDismiss,
                    contentDescription = "Close",
                    icon = Icons.Rounded.Close,
                )
        }

        AnimatedVisibility(
            visible = isLoading,
        ) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SearchActionButton(
    onClick: () -> Unit,
    contentDescription: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick, modifier = modifier.size(size), enabled = enabled
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
        )
    }
}

@Composable
private fun SearchOptionButton(
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    icon: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    IconToggleButton(
        checked = checked,
        onCheckedChange = onChecked,
        colors = IconButtonDefaults.iconToggleButtonColors(
            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = modifier.size(36.dp).semantics {
            this.contentDescription = contentDescription
        },
    ) {
        Text(
            text = icon,
            fontSize = 16.sp,
            modifier = Modifier.wrapContentSize(),
        )
    }
}
