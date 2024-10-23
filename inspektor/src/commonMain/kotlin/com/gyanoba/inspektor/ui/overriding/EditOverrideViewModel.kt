package com.gyanoba.inspektor.ui.overriding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gyanoba.inspektor.data.HttpMethod
import com.gyanoba.inspektor.data.HttpRequest
import com.gyanoba.inspektor.data.Matcher
import com.gyanoba.inspektor.data.Override
import com.gyanoba.inspektor.data.OverrideAction
import com.gyanoba.inspektor.data.OverrideRepository
import com.gyanoba.inspektor.utils.logErr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


internal class EditOverrideViewModel(
    private val repository: OverrideRepository,
    val override: Override,
) : ViewModel() {

    interface Event {
        object OverrideSaved : Event
        data class Error(val message: String) : Event
    }

    var name by mutableStateOf(override.name ?: "")
        private set
    var type by mutableStateOf(override.type)
        private set
    var matchers by mutableStateOf(override.matchers)
        private set
    var action by mutableStateOf(override.action)
        private set

    var matchersError: String? by mutableStateOf(null)
        private set
    var actionError: String? by mutableStateOf(null)
        private set
    private var isSaving: Boolean = false

    private val _events = MutableStateFlow<Event?>(null)
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
        action = when (type) {
            OverrideAction.Type.FixedRequest -> OverrideAction(
                type = type,
                requestHeaders = action.requestHeaders,
                requestBody = action.requestBody,
            )

            OverrideAction.Type.FixedResponse -> OverrideAction(
                type = type,
                statusCode = action.statusCode,
                responseHeaders = action.responseHeaders,
                responseBody = action.responseBody
            )

            OverrideAction.Type.FixedRequestResponse -> OverrideAction(
                type = type,
                requestHeaders = action.requestHeaders,
                requestBody = action.requestBody,
                statusCode = action.statusCode,
                responseHeaders = action.responseHeaders,
                responseBody = action.responseBody
            )

            OverrideAction.Type.None -> OverrideAction(type = type)
        }
    }

    fun saveOverride() = viewModelScope.launch {
        if (!validate()) return@launch
        if (isSaving) return@launch
        try {
            isSaving = true
            _events.emit(null)
            val override = override.copy(
                name = name, type = type,
                matchers = matchers, action = action,
            )
            if (override.id != 0L) {
                repository.update(override)
            } else {
                repository.add(override)
            }
            _events.emit(Event.OverrideSaved)
        } catch (e: Exception) {
            _events.emit(Event.Error(e.message ?: "Unknown error"))
            logErr(e, "EditOverrideViewModel") { "Error saving override" }
        } finally {
            isSaving = false
        }
    }

    private fun validate(): Boolean {
        var valid = true
        matchersError = null
        actionError = null
        if (matchers.isEmpty()) {
            matchersError = "At least one matcher is required"
            valid = false
        }
        val action = action
        when (action.type) {
            OverrideAction.Type.FixedRequest -> {
                if (action.requestHeaders.isEmpty() && action.requestBody.isNullOrEmpty()) {
                    actionError = "Headers and body both cannot be empty"
                    valid = false
                }
            }

            OverrideAction.Type.FixedResponse -> {
                if (action.responseHeaders.isEmpty() && action.responseBody.isNullOrEmpty() && action.statusCode == null) {
                    actionError = "Headers, body and status code all cannot be empty"
                    valid = false
                }
            }

            OverrideAction.Type.None -> {
                // No validation needed
            }

            OverrideAction.Type.FixedRequestResponse -> {
                if (action.requestHeaders.isEmpty() && action.requestBody.isNullOrEmpty() && action.responseHeaders.isEmpty() && action.responseBody.isNullOrEmpty() && action.statusCode == null) {
                    actionError =
                        "Request headers, request body, response headers, response body and status code all cannot be empty"
                    valid = false
                }
            }
        }
        return valid
    }
}