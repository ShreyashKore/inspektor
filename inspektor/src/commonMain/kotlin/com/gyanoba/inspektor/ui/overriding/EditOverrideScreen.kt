package com.gyanoba.inspektor.ui.overriding

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gyanoba.inspektor.data.FixedResponseAction
import com.gyanoba.inspektor.data.HostMatcher
import com.gyanoba.inspektor.data.HttpMethod
import com.gyanoba.inspektor.data.HttpRequest
import com.gyanoba.inspektor.data.Matcher
import com.gyanoba.inspektor.data.NoAction
import com.gyanoba.inspektor.data.Override
import com.gyanoba.inspektor.data.OverrideAction
import com.gyanoba.inspektor.data.OverrideRepositoryImpl
import com.gyanoba.inspektor.data.PathMatcher
import com.gyanoba.inspektor.data.RequestType
import com.gyanoba.inspektor.data.UrlMatcher
import com.gyanoba.inspektor.data.UrlRegexMatcher
import com.gyanoba.inspektor.data.bodyOrEmpty
import com.gyanoba.inspektor.data.copy
import com.gyanoba.inspektor.data.headersOrEmpty
import com.gyanoba.inspektor.ui.components.SimpleDropdown


@Composable
internal fun EditOverrideScreen(
    id: Long?,
    onBack: () -> Unit,
) {
    val viewModel = viewModel<EditOverrideViewModel> {
        val override = OverrideRepositoryImpl.Instance.all.firstOrNull { it.id == id }
        EditOverrideViewModel(OverrideRepositoryImpl.Instance, override ?: Override.New)
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
    EditOverrideScreen(
        id = viewModel.override.id,
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
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = name,
                        onValueChange = updateName,
                        label = { Text("Override Name") },
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    MatchersSection(
                        type = type,
                        matchers = matchers,
                        addMatcher = addMatcher,
                        removeMatcher = removeMatcher,
                        onUpdateMethod = updateHttpMethod,
                        matchersError = matchersError,
                        modifier = Modifier.padding(8.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

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
                Spacer(modifier = Modifier.height(8.dp))
                Box(Modifier.widthIn(max = 600.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        TextField(
                            value = name,
                            onValueChange = updateName,
                            label = { Text("Override Name") },
                            modifier = Modifier.weight(1f).widthIn(max = 300.dp)
                        )
                        Spacer(Modifier.width(20.dp))
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
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OverrideActionSection(
                        action = action,
                        updateOverrideAction = updateOverrideAction,
                        updateOverrideActionType = updateOverrideActionType,
                        error = actionError,
                        modifier = Modifier.padding(8.dp).fillMaxHeight().weight(1f)
                    )
                }
            }
        }
    }
}

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
            MaterialTheme.colorScheme.secondaryContainer
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
    Spacer(modifier = Modifier.height(8.dp))
    NewMatcher(onAddMatcher = addMatcher)
}


@Composable
internal fun OverrideActionSection(
    action: OverrideAction,
    updateOverrideActionType: (OverrideAction.Type) -> Unit,
    updateOverrideAction: (OverrideAction) -> Unit,
    error: String? = null,
    modifier: Modifier = Modifier,
) = Column(
    modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .background(
            MaterialTheme.colorScheme.secondaryContainer
        ).padding(8.dp).animateContentSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Row(
        Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Action",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.weight(1f))
        OverrideActionDropdown(
            actionType = action.type,
            onActionSelected = updateOverrideActionType,
        )
    }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))

    if (error != null) {
        Text(
            error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (action is NoAction) {
        Text(
            "Select an action to perform after matching the request.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            "You can choose to override Request or Response.",
            textAlign = TextAlign.Center,
        )
        return@Column
    }

    if (action is FixedResponseAction) {
        TextField(
            value = "${action.statusCode ?: ""}",
            onValueChange = { updateOverrideAction(action.copy(statusCode = it.toIntOrNull())) },
            label = { Text("Status Code") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
    Text(
        "Headers",
        style = MaterialTheme.typography.titleSmall,
    )
    Spacer(modifier = Modifier.height(8.dp))
    action.headersOrEmpty.forEach {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                it.key, Modifier.weight(.3f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(.6f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(it.value.joinToString(";"), Modifier.weight(1f))
            IconButton(
                onClick = {
                    val headers = action.headersOrEmpty.toMutableMap()
                    headers.remove(it.key)
                    updateOverrideAction(action.copy(headers = headers))
                }
            ) {
                Icon(Icons.Default.Clear, contentDescription = "Remove Header")
            }
        }
    }
    NewHeader(
        onAddHeader = {
            val headers = action.headersOrEmpty.toMutableMap()
            headers[it.name] = it.value.split(";")
            updateOverrideAction(action.copy(headers = headers))
        },
    )
    Spacer(modifier = Modifier.height(8.dp))
    TextField(
        value = action.bodyOrEmpty,
        onValueChange = {
            updateOverrideAction(action.copy(body = it))
        },
        label = { Text("Body") },
        modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp),
    )
}


@Composable
internal fun HttpMethodDropdown(
    selectedMethod: HttpMethod, onMethodSelected: (HttpMethod) -> Unit
) = SimpleDropdown(
    items = HttpMethod.currentlySupported,
    selectedItem = selectedMethod,
    onItemSelected = onMethodSelected,
    itemAsString = { "HTTP ${it.name}" },
    modifier = Modifier.widthIn(max = 160.dp),
)


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
            Text(
                when (matcher) {
                    is UrlMatcher -> "URL: ${matcher.url}"
                    is UrlRegexMatcher -> "URL Regex: ${matcher.url}"
                    is HostMatcher -> "Host: ${matcher.host}"
                    is PathMatcher -> "Path: ${matcher.path}"
                }
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onClickRemove) {
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

    fun validateAndAddMatcher() {
        if (matcherType == null) return
        if (matcherValue.isBlank()) return
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
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = matcherValue, onValueChange = { matcherValue = it },
            label = { Text(matcherToValueLabel[matcherType] ?: "Value") },
            placeholder = { Text(matcherToPlaceholders[matcherType] ?: "") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    IconButton(
        onClick = { validateAndAddMatcher() },
        enabled = matcherType != null && matcherValue.isNotBlank()
    ) {
        Icon(Icons.Default.Add, contentDescription = "Add")
    }
}

@Composable
internal fun NewHeader(onAddHeader: (NewHeader) -> Unit) = Row(
    Modifier.clip(
        RoundedCornerShape(12.dp)
    ).background(
        MaterialTheme.colorScheme.surface.copy(.5f)
    ),
    verticalAlignment = Alignment.CenterVertically,
) {
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }

    fun validateAndAddHeader() {
        if (name.isBlank()) return
        if (value.isBlank()) return
        onAddHeader(NewHeader(name, value))
        name = ""
        value = ""
    }

    Column(
        Modifier.weight(1f).padding(8.dp)
    ) {
        TextField(
            value = name, onValueChange = { name = it },
            label = { Text("Header Name") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = value, onValueChange = { value = it },
            label = { Text("Header Value") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    IconButton(
        onClick = { validateAndAddHeader() },
        enabled = name.isNotBlank() && value.isNotBlank()
    ) {
        Icon(Icons.Default.Add, contentDescription = "Add")
    }
}

internal data class NewHeader(val name: String, val value: String)

@Composable
internal fun OverrideActionDropdown(
    actionType: OverrideAction.Type, onActionSelected: (OverrideAction.Type) -> Unit
) = SimpleDropdown(
    items = OverrideAction.Type.entries,
    selectedItem = actionType,
    onItemSelected = onActionSelected,
    itemAsString = { it.label },
    modifier = Modifier.widthIn(max = 160.dp),
)


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

internal val matcherToValueLabel = mapOf(
    "UrlMatcher" to "URL",
    "UrlRegexMatcher" to "URL Regex",
    "HostMatcher" to "Host",
    "PathMatcher" to "Path",
)

internal val OverrideAction.Type.label: String
    get() = when (this) {
        OverrideAction.Type.FixedRequest -> "Fixed Request"
        OverrideAction.Type.FixedResponse -> "Fixed Response"
        OverrideAction.Type.None -> "None"
    }