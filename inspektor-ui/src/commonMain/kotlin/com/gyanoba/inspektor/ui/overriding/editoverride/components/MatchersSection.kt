package com.gyanoba.inspektor.ui.overriding.editoverride.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gyanoba.inspektor.data.HostMatcher
import com.gyanoba.inspektor.data.HttpMethod
import com.gyanoba.inspektor.data.HttpRequest
import com.gyanoba.inspektor.data.Matcher
import com.gyanoba.inspektor.data.PathMatcher
import com.gyanoba.inspektor.data.RequestType
import com.gyanoba.inspektor.data.UrlMatcher
import com.gyanoba.inspektor.data.UrlRegexMatcher
import com.gyanoba.inspektor.ui.components.Gap
import com.gyanoba.inspektor.ui.components.SimpleDropdown
import com.gyanoba.inspektor.ui.components.SimpleTextField

@Composable
internal fun MatchersSection(
    type: RequestType,
    matchers: List<Matcher>,
    addMatcher: (Matcher) -> Unit,
    removeMatcher: (Matcher) -> Unit,
    onUpdateMethod: (HttpMethod) -> Unit,
    matchersError: String? = null,
    modifier: Modifier = Modifier
) = Column(
    modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .background(
            MaterialTheme.colorScheme.surfaceContainerHighest
        ).padding(8.dp)
) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Matchers", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.weight(1f))
        HttpMethodDropdown(
            selectedMethod = (type as? HttpRequest)?.method ?: HttpMethod.Get,
            onMethodSelected = onUpdateMethod
        )
    }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    if (matchersError != null) {
        Text(
            matchersError,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
    matchers.forEach { matcher ->
        MatcherItem(matcher = matcher, onClickRemove = { removeMatcher(matcher) })
    }
    Gap(8.dp)
    NewMatcher(onAddMatcher = addMatcher)
}


@Composable
internal fun MatcherItem(
    matcher: Matcher,
    onClickRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                matcherLabels[matcher.name] ?: "Unknown Matcher",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(.6f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when(matcher) {
                        is UrlMatcher -> "URL"
                        is UrlRegexMatcher -> "URL Regex"
                        is HostMatcher -> "Host"
                        is PathMatcher -> "Path"
                    },
                    color = MaterialTheme.colorScheme.onSurface.copy(.6f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Gap(8.dp)
                Text(
                    when (matcher) {
                        is UrlMatcher -> matcher.url
                        is UrlRegexMatcher -> matcher.url
                        is HostMatcher -> matcher.host
                        is PathMatcher -> matcher.path
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        IconButton(
            onClick = onClickRemove,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface.copy(.6f)
            ),
        ) {
            Icon(Icons.Default.Clear, contentDescription = "Remove Matcher")
        }
    }
}



@Composable
internal fun NewMatcher(onAddMatcher: (Matcher) -> Unit) = Row(
    Modifier.clip(
        RoundedCornerShape(12.dp)
    ).background(
        MaterialTheme.colorScheme.surface.copy(.5f)
    ),
    verticalAlignment = Alignment.CenterVertically,
) {
    var matcherType by remember { mutableStateOf<String?>(null) }
    var matcherValue by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun validateAndAddMatcher() {
        error = validateMatcher(matcherType, matcherValue)
        if (error != null) return

        val matcher = when (matcherType) {
            "UrlMatcher" -> UrlMatcher(matcherValue)
            "UrlRegexMatcher" -> UrlRegexMatcher(matcherValue)
            "HostMatcher" -> HostMatcher(matcherValue)
            "PathMatcher" -> PathMatcher(matcherValue)
            else -> null
        }
        matcherType = null; matcherValue = ""
        matcher?.let { onAddMatcher(it) }
    }

    Column(
        Modifier.weight(1f).padding(8.dp)
    ) {
        SimpleDropdown(
            items = matcherLabels.keys.toList(),
            selectedItem = matcherType,
            onItemSelected = { matcherType = it },
            itemAsString = { matcherLabels[it] ?: "Matcher Type" },
            modifier = Modifier.fillMaxWidth(),
        )
        Gap(4.dp)
        SimpleTextField(
            value = matcherValue,
            onValueChange = { matcherValue = it },
            placeholder = matcherToPlaceholders[matcherType] ?: "Value",
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
        onClick = { validateAndAddMatcher() },
        enabled = matcherType != null && matcherValue.isNotBlank()
    ) {
        Icon(Icons.Default.Add, contentDescription = "Add")
    }
}


internal val Matcher.name: String
    get() = when (this) {
        is UrlMatcher -> "UrlMatcher"
        is UrlRegexMatcher -> "UrlRegexMatcher"
        is HostMatcher -> "HostMatcher"
        is PathMatcher -> "PathMatcher"
    }

internal val matcherLabels = mapOf(
    "UrlMatcher" to "URL Matcher",
    "UrlRegexMatcher" to "URL Regex Matcher",
    "HostMatcher" to "Host Matcher",
    "PathMatcher" to "Path Matcher",
)

internal val matcherToPlaceholders = mapOf(
    "UrlMatcher" to "http://example.com",
    "UrlRegexMatcher" to "*//example.com/.*",
    "HostMatcher" to "example.com",
    "PathMatcher" to "/path",
)


internal fun validateMatcher(matcherType: String?, matcherValue: String): String? {
    if (matcherType == null) return "Matcher type should not be empty"
    if (matcherValue.isBlank()) return "Value should not be empty"
    return when (matcherType) {
        "UrlMatcher" -> {
            if (matcherValue.startsWith("http://") || matcherValue.startsWith("https://"))
                null
            else "URL should start with http:// or https://"
        }

        "UrlRegexMatcher" -> try {
            Regex(matcherValue)
            null
        } catch (e: Exception) {
            e.message
        }

        "HostMatcher" -> {
            if (matcherValue.isNotBlank() && !matcherValue.contains(" "))
                null
            else "Host should not contain spaces"

        }

        "PathMatcher" -> {
            if (matcherValue.startsWith("/")) null
            else "Path should start with /"
        }

        else -> null
    }
}
