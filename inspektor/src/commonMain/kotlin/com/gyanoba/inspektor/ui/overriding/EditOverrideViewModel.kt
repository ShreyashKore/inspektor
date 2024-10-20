package com.gyanoba.inspektor.ui.overriding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gyanoba.inspektor.data.FixedRequestAction
import com.gyanoba.inspektor.data.FixedResponseAction
import com.gyanoba.inspektor.data.HttpMethod
import com.gyanoba.inspektor.data.HttpRequest
import com.gyanoba.inspektor.data.Matcher
import com.gyanoba.inspektor.data.NoAction
import com.gyanoba.inspektor.data.Override
import com.gyanoba.inspektor.data.OverrideAction
import com.gyanoba.inspektor.data.OverrideRepository
import com.gyanoba.inspektor.data.bodyOrEmpty
import com.gyanoba.inspektor.data.headersOrEmpty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


internal class EditOverrideViewModel(
    private val repository: OverrideRepository,
    val override: Override,
) : ViewModel() {

    var name by mutableStateOf(override.name ?: "")
        private set
    var type by mutableStateOf(override.type)
        private set
    var matchers by mutableStateOf(override.matchers)
        private set
    var action by mutableStateOf(override.action)
        private set
    var actionType by mutableStateOf(override.action.type)
        private set
    var enabled by mutableStateOf(override.enabled)
        private set

    private val _events = MutableStateFlow<Any?>(null)
    val events = _events.asStateFlow()

    fun updateOverrideAction(action: OverrideAction) {
        this.action = action
    }

    fun updateName(name: String) {
        this.name = name
    }

    fun updateHttpMethod(method: HttpMethod) {
        this.type = HttpRequest(method)
    }

    fun addMatcher(matcher: Matcher) {
        this.matchers += matcher
    }

    fun removeMatcher(matcher: Matcher) {
        this.matchers -= matcher
    }

    fun updateOverrideActionType(type: OverrideAction.Type) {
        actionType = type
        action = when (type) {
            OverrideAction.Type.FixedRequest -> FixedRequestAction(
                headers = action.headersOrEmpty,
                body = action.bodyOrEmpty
            )

            OverrideAction.Type.FixedResponse -> FixedResponseAction(
                headers = action.headersOrEmpty,
                body = action.bodyOrEmpty,
                statusCode = (action as? FixedResponseAction)?.statusCode
            )

            OverrideAction.Type.None -> NoAction
        }
    }

    fun saveOverride() = viewModelScope.launch {
        if (override.id != 0L) {
            repository.update(override.copy(
                name = name,
                type = type,
                matchers = matchers,
                action = action,
                enabled = enabled
            ))
        } else {
            val newOverride = Override(
                id = 0L,
                type = type,
                matchers = matchers,
                action = action,
                name = name,
                enabled = enabled
            )
            repository.add(newOverride)
            _events.emit(Any())
        }
    }
}

internal val OverrideAction.type: OverrideAction.Type
    get() = when (this) {
        is FixedRequestAction -> OverrideAction.Type.FixedRequest
        is FixedResponseAction -> OverrideAction.Type.FixedResponse
        NoAction -> OverrideAction.Type.None
    }