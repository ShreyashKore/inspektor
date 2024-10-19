package com.gyanoba.inspektor.ui.overriding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gyanoba.inspektor.data.HostMatcher
import com.gyanoba.inspektor.data.Override
import com.gyanoba.inspektor.data.OverrideRepository
import com.gyanoba.inspektor.data.PathMatcher
import com.gyanoba.inspektor.data.UrlMatcher
import com.gyanoba.inspektor.data.UrlRegexMatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


internal class OverridesListViewModel(
    private val overrideRepository: OverrideRepository,
) : ViewModel() {
    private val overrides = overrideRepository.updates

    private val searchTerm = MutableStateFlow("")

    @OptIn(FlowPreview::class)
    val visibleOverrides = overrides.debounce(500).map { overrides ->
        if (searchTerm.value.isBlank()) {
            return@map overrides
        }

        overrides.filter { override ->
            (override.name?.contains(searchTerm.value, ignoreCase = true)
                ?: true) || override.matchers.any {
                when (it) {
                    is PathMatcher -> it.path.contains(searchTerm.value, ignoreCase = true)
                    is HostMatcher -> it.host.contains(searchTerm.value, ignoreCase = true)
                    is UrlMatcher -> it.url.contains(searchTerm.value, ignoreCase = true)
                    is UrlRegexMatcher -> it.url.contains(searchTerm.value, ignoreCase = true)
                }
            }
        }
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(4000), emptyList()
    )

    fun search(query: String) {
        searchTerm.value = query
    }

    fun deleteOverride(override: Override) = viewModelScope.launch {
        overrideRepository.remove(override)
    }

    fun toggleEnableDisable(override: Override) = viewModelScope.launch {
        overrideRepository.update(override.copy(enabled = !override.enabled))
    }
}