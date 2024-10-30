package com.gyanoba.inspektor.ui.overriding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gyanoba.inspektor.data.HostMatcher
import com.gyanoba.inspektor.data.Override
import com.gyanoba.inspektor.data.OverrideAction
import com.gyanoba.inspektor.data.OverrideRepositoryImpl
import com.gyanoba.inspektor.data.PathMatcher
import com.gyanoba.inspektor.data.UrlMatcher
import com.gyanoba.inspektor.data.UrlRegexMatcher
import com.gyanoba.inspektor.ui.components.SimpleSearchBar

@Composable
internal fun OverridesListScreen(
    openEditOverrideScreen: (Long?) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel =
        viewModel<OverridesListViewModel> { OverridesListViewModel(OverrideRepositoryImpl.Instance) }

    OverridesListScreen(
        overrides = viewModel.visibleOverrides.collectAsState().value,
        searchFieldState = viewModel.searchFieldState,
        deleteOverride = viewModel::deleteOverride,
        openAddOverrideScreen = openEditOverrideScreen,
        toggleEnableDisableOverride = viewModel::toggleEnableDisable,
        onBack = onBack,
    )

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OverridesListScreen(
    overrides: List<Override>,
    searchFieldState: TextFieldState,
    deleteOverride: (Override) -> Unit,
    openAddOverrideScreen: (Long?) -> Unit,
    toggleEnableDisableOverride: (Override) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Overrides") },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Add Override") },
                icon = { Icon(Icons.Rounded.Add, contentDescription = "Add Override") },
                onClick = { openAddOverrideScreen(null) },
            )
        },
    ) {
        Column(
            modifier = Modifier.padding(it)
        ) {
            SimpleSearchBar(
                searchFieldState = searchFieldState,
                placeholder = { Text("Search Overrides") },
                modifier = Modifier.padding(8.dp),
            )

            LazyColumn {
                items(overrides) { override ->
                    OverrideRow(
                        override = override,
                        deleteOverride = { deleteOverride(override) },
                        editOverride = { openAddOverrideScreen(override.id) },
                        toggleEnableDisable = { toggleEnableDisableOverride(override) },
                    )
                }
            }

        }
    }
}

@Composable
internal fun OverrideRow(
    override: Override,
    deleteOverride: () -> Unit,
    editOverride: () -> Unit,
    toggleEnableDisable: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    override.name.takeIf { it.isNullOrBlank().not() } ?: "[Unnamed]",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            IconButton(onClick = deleteOverride) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete")
            }
            IconButton(onClick = editOverride) {
                Icon(Icons.Rounded.Edit, contentDescription = "Edit")
            }
            Switch(checked = override.enabled, onCheckedChange = { toggleEnableDisable(it) })
        }

        Column(
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {

            Text(
                "Matchers (${override.matchers.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            override.matchers.take(3).map { matcher ->
                when (matcher) {
                    is PathMatcher -> SecondaryText("Path: ${matcher.path}")
                    is HostMatcher -> SecondaryText("Host: ${matcher.host}")
                    is UrlMatcher -> SecondaryText("Url: ${matcher.url}")
                    is UrlRegexMatcher -> SecondaryText("Url Regex: ${matcher.url}")
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                override.action.type.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            override.action.prettyPrintList().map {
                SecondaryText(it)
            }
            Spacer(Modifier.height(8.dp))
        }

    }
}

@Composable
private fun SecondaryText(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    maxLines = 1,
)

internal fun OverrideAction.prettyPrintList(): List<String> = when (this.type) {
    OverrideAction.Type.FixedRequest -> buildList {
        if (requestHeaders.isNotEmpty()) add("Headers: ${requestHeaders.prettyPrint()}")
        if (!requestBody.isNullOrBlank()) add("Body: $requestBody")
    }

    OverrideAction.Type.FixedResponse -> buildList {
        if (statusCode != null) add("Status: $statusCode")
        if (responseHeaders.isNotEmpty()) add("Headers: ${responseHeaders.prettyPrint()}")
        if (!responseBody.isNullOrBlank()) add("Body: $responseBody")
    }

    OverrideAction.Type.None -> emptyList()
    OverrideAction.Type.FixedRequestResponse -> buildList {
        if (requestHeaders.isNotEmpty()) add("Request Headers: ${requestHeaders.prettyPrint()}")
        if (!requestBody.isNullOrBlank()) add("Request Body: $requestBody")
        if (statusCode != null) add("Status: $statusCode")
        if (responseHeaders.isNotEmpty()) add("Response Headers: ${responseHeaders.prettyPrint()}")
        if (!responseBody.isNullOrBlank()) add("Response Body: $responseBody")
    }
}

internal fun Map<String, List<String>>.prettyPrint(): String =
    entries.joinToString { "${it.key} : ${it.value.joinToString(";") { it }}" }
