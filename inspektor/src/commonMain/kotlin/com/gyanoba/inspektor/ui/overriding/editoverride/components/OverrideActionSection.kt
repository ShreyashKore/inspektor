package com.gyanoba.inspektor.ui.overriding.editoverride.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gyanoba.inspektor.data.OverrideAction
import com.gyanoba.inspektor.ui.components.Gap
import com.gyanoba.inspektor.ui.components.SimpleDropdown
import com.gyanoba.inspektor.ui.overriding.editoverride.label


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
            MaterialTheme.colorScheme.surfaceContainerHighest
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

    Gap(8.dp)

    if (action.type == OverrideAction.Type.None) {
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

    if (action.type == OverrideAction.Type.FixedRequestResponse || action.type == OverrideAction.Type.FixedRequest) {
        Text(
            "Request",
            style = MaterialTheme.typography.titleSmall,
        )
        Gap(8.dp)
        StatusRequestResponseEdit(
            value = StatusRequestResponse(
                statusCode = null,
                headers = action.requestHeaders,
                body = action.requestBody
            ),
            updateValue = {
                updateOverrideAction(
                    action.copy(
                        requestHeaders = it.headers,
                        requestBody = it.body
                    )
                )
            },
            showStatusCode = false
        )
    }

    if (action.type == OverrideAction.Type.FixedRequestResponse || action.type == OverrideAction.Type.FixedResponse) {
        Gap(16.dp)
        Text(
            "Response",
            style = MaterialTheme.typography.titleSmall,
        )
        Gap(8.dp)
        StatusRequestResponseEdit(
            value = StatusRequestResponse(
                statusCode = action.statusCode,
                headers = action.responseHeaders,
                body = action.responseBody
            ),
            updateValue = {
                updateOverrideAction(
                    action.copy(
                        statusCode = it.statusCode,
                        responseHeaders = it.headers,
                        responseBody = it.body
                    )
                )
            },
            showStatusCode = true,
        )
    }
}


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