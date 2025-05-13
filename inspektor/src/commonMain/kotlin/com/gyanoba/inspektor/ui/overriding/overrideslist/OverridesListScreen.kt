package com.gyanoba.inspektor.ui.overriding.overrideslist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gyanoba.inspektor.data.Override
import com.gyanoba.inspektor.data.OverrideRepositoryImpl
import com.gyanoba.inspektor.ui.components.SimpleSearchBar
import com.gyanoba.inspektor.ui.overriding.overrideslist.components.OverrideRow
import kotlinx.coroutines.launch

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
        undoDeleteOverride = viewModel::undoDeleteOverride,
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
    undoDeleteOverride: (Override) -> Unit,
    openAddOverrideScreen: (Long?) -> Unit,
    toggleEnableDisableOverride: (Override) -> Unit,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun onDeleteOverride(override: Override) {
        deleteOverride(override)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                "Override ${override.name} deleted!",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                undoDeleteOverride(override)
            }
        }
    }
    Scaffold(
        snackbarHost = {
            SnackbarHost(
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentHeight(Alignment.Bottom),
                hostState = snackbarHostState,
                snackbar = { data ->
                    Snackbar(snackbarData = data)
                }
            )
        },
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
                        deleteOverride = { onDeleteOverride(override) },
                        editOverride = { openAddOverrideScreen(override.id) },
                        toggleEnableDisable = { toggleEnableDisableOverride(override) },
                    )
                }
            }

        }
    }
}


internal fun Map<String, List<String>>.prettyPrint(): String =
    entries.joinToString { "${it.key} : ${it.value.joinToString(";") { it }}" }
