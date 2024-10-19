package com.gyanoba.inspektor.ui.overriding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gyanoba.inspektor.data.FixedRequestAction
import com.gyanoba.inspektor.data.FixedResponseAction
import com.gyanoba.inspektor.data.HostMatcher
import com.gyanoba.inspektor.data.Override
import com.gyanoba.inspektor.data.OverrideRepositoryImpl
import com.gyanoba.inspektor.data.PathMatcher
import com.gyanoba.inspektor.data.UrlMatcher
import com.gyanoba.inspektor.data.UrlRegexMatcher
import com.gyanoba.inspektor.ui.components.SimpleSearchBar

@Composable
internal fun OverridesListScreen(
    openAddOverrideScreen: () -> Unit,
) {
    val viewModel =
        viewModel<OverridesListViewModel> { OverridesListViewModel(OverrideRepositoryImpl.Instance) }

    OverridesListScreen(
        overrides = viewModel.visibleOverrides.value,
        searchFieldState = viewModel.searchFieldState,
        deleteOverride = viewModel::deleteOverride,
        openAddOverrideScreen = openAddOverrideScreen,
        toggleEnableDisableOverride = viewModel::toggleEnableDisable,
    )

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OverridesListScreen(
    overrides: List<Override>,
    searchFieldState: TextFieldState,
    deleteOverride: (Override) -> Unit,
    openAddOverrideScreen: () -> Unit,
    toggleEnableDisableOverride: (Override) -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Overrides") })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = openAddOverrideScreen,
            ) { Text("Add Override") }
        },
    ) {
        Column(
            modifier = Modifier.padding(it)
        ) {
            SimpleSearchBar(
                searchFieldState = searchFieldState,
                placeholder = { Text("Search Overrides") },
                modifier = Modifier.padding(16.dp),
            )

            LazyColumn {
                items(overrides) { override ->
                    OverrideRow(
                        override = override,
                        deleteOverride = { deleteOverride(override) },
                        toggleEnableDisable = { toggleEnableDisableOverride(override) },
                    )
                    HorizontalDivider()
                }
            }

        }
    }
}

@Composable
internal fun OverrideRow(
    override: Override,
    deleteOverride: () -> Unit,
    toggleEnableDisable: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.padding(16.dp)
    ) {
        Row {
            Text(
                override.name ?: "Unnamed",
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = deleteOverride) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete")
            }
            Switch(checked = override.enabled, onCheckedChange = { toggleEnableDisable(it) })
        }

        Text(override.matchers.joinToString("\n", prefix = "- ") { matcher ->
            when (matcher) {
                is PathMatcher -> "Path: ${matcher.path}".take(20)
                is HostMatcher -> "Host: ${matcher.host}".take(20)
                is UrlMatcher -> "Url: ${matcher.url}".take(20)
                is UrlRegexMatcher -> "Url Regex: ${matcher.url}".take(20)
            }
        })

        Column {
            Text(
                when (override.action) {
                    is FixedRequestAction -> "Fixed Request"
                    is FixedResponseAction -> "Fixed Response"
                },
            )

            Text(
                when (val action = override.action) {
                    is FixedRequestAction ->
                        "Headers: ${action.headers}".take(20) + "\n" +
                                "Body: ${action.body}".take(20)

                    is FixedResponseAction ->
                        "Status: ${action.statusCode}".take(20) + "\n" +
                                "Headers: ${action.headers}".take(20) + "\n" +
                                "Body: ${action.body}".take(20)
                }
            )

        }

    }
}

