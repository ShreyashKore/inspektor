package com.gyanoba.inspektor.ui.overriding.editoverride

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gyanoba.inspektor.data.HttpMethod
import com.gyanoba.inspektor.data.InspektorDataSourceImpl
import com.gyanoba.inspektor.data.Matcher
import com.gyanoba.inspektor.data.OverrideAction
import com.gyanoba.inspektor.data.OverrideRepositoryImpl
import com.gyanoba.inspektor.data.RequestType
import com.gyanoba.inspektor.ui.components.Gap
import com.gyanoba.inspektor.ui.overriding.editoverride.components.MatchersSection
import com.gyanoba.inspektor.ui.overriding.editoverride.components.OverrideActionSection


@Composable
internal fun EditOverrideScreen(
    overrideId: Long,
    onBack: () -> Unit,
    transactionId: Long? = null,
) {
    val viewModel = viewModel<EditOverrideViewModel> {
        EditOverrideViewModel(
            OverrideRepositoryImpl.Instance,
            InspektorDataSourceImpl.Instance,
            overrideId = overrideId,
            sourceTransactionId = transactionId
        )
    }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                EditOverrideViewModel.Event.OverrideSaved -> onBack()
                is EditOverrideViewModel.Event.Error -> snackbarHostState.showSnackbar("Error: ${event.message}")
            }
        }
    }
    if (viewModel.isLoading) {
        Box(Modifier.fillMaxSize()) {
            CircularProgressIndicator()
        }
        return
    }
    EditOverrideScreen(
        id = viewModel.overrideId,
        name = viewModel.name,
        type = viewModel.type,
        matchers = viewModel.matchers,
        action = viewModel.action,
        matchersError = viewModel.matchersError,
        actionError = viewModel.actionError,
        snackbarHostState = snackbarHostState,
        updateName = viewModel::updateName,
        updateHttpMethod = viewModel::updateHttpMethod,
        addMatcher = viewModel::addMatcher,
        removeMatcher = viewModel::removeMatcher,
        updateOverrideActionType = viewModel::updateOverrideActionType,
        updateOverrideAction = viewModel::updateOverrideAction,
        saveOverride = viewModel::saveOverride,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditOverrideScreen(
    id: Long,
    name: String,
    type: RequestType,
    matchers: List<Matcher>,
    action: OverrideAction,
    matchersError: String?,
    actionError: String?,
    snackbarHostState: SnackbarHostState,
    updateName: (String) -> Unit,
    updateHttpMethod: (HttpMethod) -> Unit,
    addMatcher: (Matcher) -> Unit,
    removeMatcher: (Matcher) -> Unit,
    updateOverrideActionType: (OverrideAction.Type) -> Unit,
    updateOverrideAction: (OverrideAction) -> Unit,
    saveOverride: () -> Unit,
    onBack: () -> Unit,
) = BoxWithConstraints {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(if (id == 0L) "Add Override" else "Edit Override")
                },
            )
        },
    ) {
        if (maxWidth < 600.dp) {
            Column {
                Column(
                    Modifier.padding(it).weight(1f).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Gap(8.dp)
                    OutlinedTextField(
                        value = name,
                        onValueChange = updateName,
                        label = { Text("Override Name") },
                    )

                    Gap(16.dp)

                    MatchersSection(
                        type = type,
                        matchers = matchers,
                        addMatcher = addMatcher,
                        removeMatcher = removeMatcher,
                        onUpdateMethod = updateHttpMethod,
                        matchersError = matchersError,
                        modifier = Modifier.padding(8.dp)
                    )

                    Gap(4.dp)

                    OverrideActionSection(
                        action = action,
                        updateOverrideActionType = updateOverrideActionType,
                        updateOverrideAction = updateOverrideAction,
                        error = actionError,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                Button(
                    onClick = saveOverride,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save Override")
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(it),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Gap(8.dp)
                Box(Modifier.widthIn(max = 600.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.widthIn(max = 500.dp)
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = updateName,
                            label = { Text("Override Name") },
                            modifier = Modifier.weight(1f),
                        )
                        Gap(20.dp)
                        Button(onClick = saveOverride) {
                            Text("Save Override")
                        }
                    }

                }
                Row(
                    Modifier.weight(1f)
                ) {
                    MatchersSection(
                        type = type,
                        matchers = matchers,
                        matchersError = matchersError,
                        addMatcher = addMatcher,
                        removeMatcher = removeMatcher,
                        onUpdateMethod = updateHttpMethod,
                        modifier = Modifier.padding(8.dp).fillMaxHeight().weight(1f)
                            .verticalScroll(rememberScrollState())
                    )

                    Gap(8.dp)

                    OverrideActionSection(
                        action = action,
                        updateOverrideAction = updateOverrideAction,
                        updateOverrideActionType = updateOverrideActionType,
                        error = actionError,
                        modifier = Modifier.padding(8.dp).fillMaxHeight().weight(1f)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}


internal val OverrideAction.Type.label: String
    get() = when (this) {
        OverrideAction.Type.FixedRequest -> "Fixed Request"
        OverrideAction.Type.FixedResponse -> "Fixed Response"
        OverrideAction.Type.None -> "None"
        OverrideAction.Type.FixedRequestResponse -> "Fixed Request & Response"
    }

