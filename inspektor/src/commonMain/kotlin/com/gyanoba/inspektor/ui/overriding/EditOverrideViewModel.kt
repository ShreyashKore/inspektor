package com.gyanoba.inspektor.ui.overriding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gyanoba.inspektor.data.FixedResponseAction
import com.gyanoba.inspektor.data.HttpMethod
import com.gyanoba.inspektor.data.HttpRequest
import com.gyanoba.inspektor.data.Matcher
import com.gyanoba.inspektor.data.Override
import com.gyanoba.inspektor.data.OverrideAction
import com.gyanoba.inspektor.data.OverrideRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


internal class EditOverrideViewModel(
    private val repository: OverrideRepository,
    private val overrideId: Long?,
) : ViewModel() {
    private val _currentOverride = MutableStateFlow(
        overrideId?.let {
            repository.all.firstOrNull { it.id == overrideId }
        } ?: Override(
            id = overrideId ?: repository.all.lastOrNull()?.id?.plus(1) ?: 1,
            type = HttpRequest(HttpMethod.Get),
            matchers = emptyList(),
            action = FixedResponseAction(),
            name = "",
            enabled = true
        )
    )

    val currentOverride: StateFlow<Override> = _currentOverride

    fun updateName(name: String) {
        _currentOverride.update { it.copy(name = name) }
    }

    fun updateHttpMethod(method: HttpMethod) {
        _currentOverride.update { it.copy(type = HttpRequest(method)) }
    }

    fun addMatcher(matcher: Matcher) {
        val newMatchers = _currentOverride.value.matchers + matcher
        _currentOverride.update { it.copy(matchers = newMatchers) }
    }

    fun removeMatcher(matcher: Matcher) {
        val newMatchers = _currentOverride.value.matchers.toMutableList()
        newMatchers.remove(matcher)
        _currentOverride.update {
            it.copy(matchers = newMatchers)
        }
    }

    fun updateOverrideAction(action: OverrideAction) {
        _currentOverride.update { it.copy(action = action) }
    }

    fun saveOverride() = viewModelScope.launch {
        val newOverride = _currentOverride.value
        repository.add(newOverride)
        resetCurrentOverride()
    }

    private fun resetCurrentOverride() {
        _currentOverride.update {
            Override(
                id = repository.all.maxOfOrNull { it.id } ?: 1,
                type = HttpRequest(HttpMethod.Get),
                matchers = emptyList(),
                action = FixedResponseAction(),
                name = "",
                enabled = true
            )
        }
    }
}