package com.gyanoba.inspektor.ui.overriding

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gyanoba.inspektor.data.FixedRequestAction
import com.gyanoba.inspektor.data.FixedResponseAction
import com.gyanoba.inspektor.data.HostMatcher
import com.gyanoba.inspektor.data.HttpMethod
import com.gyanoba.inspektor.data.HttpRequest
import com.gyanoba.inspektor.data.Matcher
import com.gyanoba.inspektor.data.Override
import com.gyanoba.inspektor.data.OverrideAction
import com.gyanoba.inspektor.data.OverrideRepositoryImpl
import com.gyanoba.inspektor.data.PathMatcher
import com.gyanoba.inspektor.data.UrlMatcher
import com.gyanoba.inspektor.data.UrlRegexMatcher


@Composable
internal fun EditOverrideScreen(
    id: Long?,
    onBack: () -> Unit,
) {
    val viewModel = viewModel<EditOverrideViewModel> {
        EditOverrideViewModel(OverrideRepositoryImpl.Instance, id)
    }
    EditOverrideScreen(
        currentOverride = viewModel.currentOverride.collectAsState().value,
        updateName = viewModel::updateName,
        updateHttpMethod = viewModel::updateHttpMethod,
        addMatcher = viewModel::addMatcher,
        removeMatcher = viewModel::removeMatcher,
        updateOverrideAction = viewModel::updateOverrideAction,
        saveOverride = viewModel::saveOverride,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditOverrideScreen(
    currentOverride: Override,
    updateName: (String) -> Unit,
    updateHttpMethod: (HttpMethod) -> Unit,
    addMatcher: (Matcher) -> Unit,
    removeMatcher: (Matcher) -> Unit,
    updateOverrideAction: (OverrideAction) -> Unit,
    saveOverride: () -> Unit,
    onBack: () -> Unit,
) = BoxWithConstraints {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text("Add Override")
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
                    Spacer(modifier = Modifier.height(16.dp))
                    TextField(
                        value = currentOverride.name ?: "",
                        onValueChange = updateName,
                        label = { Text("Override Name") },
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    MatchersSection(
                        currentOverride = currentOverride,
                        addMatcher = addMatcher,
                        removeMatcher = removeMatcher,
                        onUpdateMethod = updateHttpMethod,
                        modifier = Modifier.padding(4.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    OverrideActionSection(
                        currentOverride = currentOverride,
                        updateOverrideAction = updateOverrideAction,
                        modifier = Modifier.padding(4.dp)
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
                Spacer(modifier = Modifier.height(16.dp))
                Box(Modifier.widthIn(max = 600.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        TextField(
                            value = currentOverride.name ?: "",
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
                        currentOverride = currentOverride,
                        addMatcher = addMatcher,
                        removeMatcher = removeMatcher,
                        onUpdateMethod = updateHttpMethod,
                        modifier = Modifier.padding(8.dp).fillMaxHeight().weight(1f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OverrideActionSection(
                        currentOverride = currentOverride,
                        updateOverrideAction = updateOverrideAction,
                        modifier = Modifier.padding(8.dp).fillMaxHeight().weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
internal fun MatchersSection(
    currentOverride: Override,
    addMatcher: (Matcher) -> Unit,
    removeMatcher: (Matcher) -> Unit,
    onUpdateMethod: (HttpMethod) -> Unit,
    modifier: Modifier = Modifier
) = Column(
    modifier.fillMaxWidth().background(
        MaterialTheme.colorScheme.secondaryContainer
    ).clip(RoundedCornerShape(12.dp)).padding(16.dp)
) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Matchers", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.weight(1f))
        HttpMethodDropdown(
            selectedMethod = (currentOverride.type as? HttpRequest)?.method ?: HttpMethod.Get,
            onMethodSelected = onUpdateMethod
        )
    }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    currentOverride.matchers.forEach { matcher ->
        MatcherItem(matcher = matcher, onClickRemove = { removeMatcher(matcher) })
    }
    Spacer(modifier = Modifier.height(8.dp))
    NewMatcher(onAddMatcher = addMatcher)
}


@Composable
internal fun OverrideActionSection(
    currentOverride: Override,
    updateOverrideAction: (OverrideAction) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier.fillMaxWidth().background(
        MaterialTheme.colorScheme.secondaryContainer
    ).clip(RoundedCornerShape(12.dp)).padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    val selectedAction = currentOverride.action
    var actionType by remember { mutableStateOf(if (selectedAction is FixedRequestAction) "Fixed Request" else "Fixed Response") }
    var statusCode by remember {
        mutableStateOf(
            (selectedAction as? FixedResponseAction)?.statusCode?.toString() ?: ""
        )
    }
    var body by remember {
        mutableStateOf(
            when (selectedAction) {
                is FixedRequestAction -> selectedAction.body
                is FixedResponseAction -> selectedAction.body
            } ?: ""
        )
    }

    var headers by remember {
        mutableStateOf(
            when (selectedAction) {
                is FixedRequestAction -> selectedAction.headers
                is FixedResponseAction -> selectedAction.headers
            }
        )
    }


    Row(
        Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Action",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.weight(1f))
        OverrideActionDropdown(
            actionType = actionType,
            onActionSelected = {
                actionType = it
            },
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    AnimatedVisibility(actionType == "FixedResponse") {
        TextField(
            value = statusCode,
            onValueChange = { statusCode = it },
            label = { Text("Status Code") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Text(
        "Headers",
        style = MaterialTheme.typography.titleSmall,
    )
    Spacer(modifier = Modifier.height(8.dp))
    headers.forEach {
        Row {
            Text(
                it.key, Modifier.weight(.3f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(.6f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(it.value.joinToString(";"), Modifier.weight(1f))
            IconButton(
                onClick = {
                    headers = headers - it.key
                }
            ) {
                Icon(Icons.Default.Clear, contentDescription = "Remove Header")
            }
        }
    }
    NewHeader(
        onAddHeader = {
            headers = headers + (it.name to headers[it.name].orEmpty() + listOf(it.value))
        },
    )
    Spacer(modifier = Modifier.height(8.dp))
    TextField(
        value = body,
        onValueChange = { body = it },
        label = { Text("Body") },
        modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp),
    )
    Spacer(modifier = Modifier.height(8.dp))
    Button(onClick = {
        val action = if (actionType == "FixedRequest") {
            FixedRequestAction(
                headers = headers,
                body = body,
            )
        } else {
            FixedResponseAction(
                statusCode = statusCode.toIntOrNull(),
                headers = headers,
                body = body,
            )
        }
        updateOverrideAction(action)
    }) {
        Text("Set Override Action")
    }
    Spacer(modifier = Modifier.height(16.dp))

}


@Composable
internal fun HttpMethodDropdown(
    selectedMethod: HttpMethod, onMethodSelected: (HttpMethod) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(onClick = { expanded = true }, Modifier.widthIn(min = 200.dp)) {
            Text("HTTP ${selectedMethod.name}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            HttpMethod.currentlySupported.forEach { method ->
                DropdownMenuItem(
                    onClick = {
                        onMethodSelected(method)
                        expanded = false
                    },
                    text = { Text("HTTP ${method.name}") },
                )
            }
        }
    }
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
    var showDropdown by remember { mutableStateOf(false) }

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
        Box {
            OutlinedButton(
                onClick = { showDropdown = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(matcherLabels[matcherType] ?: "Matcher Type")
            }

            DropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false },
                modifier = Modifier,
            ) {
                matcherLabels.forEach { (matcher, name) ->
                    DropdownMenuItem(onClick = {
                        matcherType = matcher
                        showDropdown = false
                    }, text = {
                        Text(name)
                    })
                }
            }
        }

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
    actionType: String, onActionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
        ) {
            Text(
                when (actionType) {
                    "FixedRequest" -> "Fixed Request"
                    "FixedResponse" -> "Fixed Response"
                    else -> "Override Action"
                }
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(onClick = { onActionSelected("FixedRequest") }, text = {
                Text("Fixed Request")
            })
            DropdownMenuItem(onClick = { onActionSelected("FixedResponse") }, text = {
                Text("Fixed Response")
            })
        }
    }
}

@Composable
internal fun OverrideItem(override: Override) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Name: ${override.name ?: "Unnamed"}")
            Text("Type: ${(override.type as? HttpRequest)?.method?.name ?: "Unknown"}")
            Text("Matchers:")
            override.matchers.forEach { matcher ->
                Text(
                    "  ${
                        when (matcher) {
                            is UrlMatcher -> "URL: ${matcher.url}"
                            is UrlRegexMatcher -> "URL Regex: ${matcher.url}"
                            is HostMatcher -> "Host: ${matcher.host}"
                            is PathMatcher -> "Path: ${matcher.path}"
                        }
                    }"
                )
            }
            Text(
                "Override Action: ${
                    when (override.action) {
                        is FixedRequestAction -> "Fixed Request"
                        is FixedResponseAction -> "Fixed Response"
                    }
                }"
            )
            when (val action = override.action) {
                is FixedRequestAction -> {
                    Text("Body: ${action.body ?: "None"}")
                }

                is FixedResponseAction -> {
                    Text("Status Code: ${action.statusCode ?: "Not set"}")
                    Text("Body: ${action.body ?: "None"}")
                }
            }
            Text("Enabled: ${override.enabled}")
        }
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

internal val matcherToValueLabel = mapOf(
    "UrlMatcher" to "URL",
    "UrlRegexMatcher" to "URL Regex",
    "HostMatcher" to "Host",
    "PathMatcher" to "Path",
)
