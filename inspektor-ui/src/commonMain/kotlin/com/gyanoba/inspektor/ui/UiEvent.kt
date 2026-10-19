package com.gyanoba.inspektor.ui

internal sealed class UiEvent {
    data object NoEvent : UiEvent()
    data class ShowSnackBar(val message: String) : UiEvent()
    data class ShowErrorDialog(val message: String) : UiEvent()
}