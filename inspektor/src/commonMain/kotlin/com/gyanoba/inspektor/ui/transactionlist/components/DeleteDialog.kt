package com.gyanoba.inspektor.ui.transactionlist.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import com.gyanoba.inspektor.utils.DateFormatters
import com.gyanoba.inspektor.utils.atLocalStartOfDay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeleteDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (Instant) -> Unit,
) {
    val datePickerState = rememberDatePickerState(initialDisplayMode = DisplayMode.Input)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Delete Transactions") },
        text = {
            Column {
                Text("Are you sure you want to delete all transactions ${
                    datePickerState.selectedDateInstant?.atLocalStartOfDay(TimeZone.currentSystemDefault())
                        ?.toLocalDateTime(TimeZone.currentSystemDefault())?.date
                        ?.format(DateFormatters.simpleLocalFormatter)?.let { "before $it" } ?: ""
                }?")
                DatePicker(
                    title = null,
                    headline = null,
                    showModeToggle = false,
                    state = datePickerState,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        datePickerState.selectedDateInstant
                            ?.atLocalStartOfDay(TimeZone.currentSystemDefault())
                            ?: Clock.System.now()
                    )
                    onDismissRequest()
                }
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}



@OptIn(ExperimentalMaterial3Api::class)
internal val DatePickerState.selectedDateInstant: Instant?
    get() = selectedDateMillis?.let { Instant.fromEpochMilliseconds(it) }