package com.gyanoba.inspektor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gyanoba.inspektor.utils.log
import kotlinx.datetime.Instant


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateRangePickerDialog(
    startDate: Instant,
    endDate: Instant,
    onDismissRequest: () -> Unit,
    onConfirm: (Instant, Instant) -> Unit
) {
    val dateRangePickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = startDate.toEpochMilliseconds(),
        initialSelectedEndDateMillis = endDate.toEpochMilliseconds(),
    )
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier.background(
                MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp)
            )
        ) {
            DateRangePicker(dateRangePickerState)
            Button(
                onClick = {
                    val selectedStartDate = dateRangePickerState.selectedStartDateMillis?.let {
                        Instant.fromEpochMilliseconds(it)
                    } ?: startDate
                    val selectedEndDate = dateRangePickerState.selectedEndDateMillis?.let {
                        Instant.fromEpochMilliseconds(it)
                    } ?: endDate
                    onDismissRequest()
                    log { "selectedStartDate $selectedStartDate selectedEndDate $selectedEndDate" }
                    onConfirm(selectedStartDate, selectedEndDate)
                },
                Modifier.align(Alignment.BottomEnd),
            ) {
                Text("Select")
            }
        }
    }
}