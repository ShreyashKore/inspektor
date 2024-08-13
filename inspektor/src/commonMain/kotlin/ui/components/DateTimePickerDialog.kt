package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeFormat
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import utils.DateFormatters
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateRangePickerDialog(
    startDate: Instant,
    endDate: Instant,
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit = {},
    onConfirm: (Instant, Instant) -> Unit,
) {
    val startDateUtc = startDate + startDate.getUtcOffsetDuration()

    val safeEndDateUtc = run {
        val endDateUtc = endDate + endDate.getUtcOffsetDuration()
        if (endDateUtc < startDateUtc) startDateUtc else endDateUtc
    }
    val dateRangePickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = startDateUtc.toEpochMilliseconds(),
        initialSelectedEndDateMillis = safeEndDateUtc.toEpochMilliseconds(),
    )
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = modifier
    ) {
        Column(
            Modifier.padding(12.dp).background(
                MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp)
            )
        ) {
            DateRangePicker(
                dateRangePickerState,
                Modifier.heightIn(max = 640.dp).weight(1f, fill = false),
            )
            Row(
                Modifier.fillMaxWidth().padding(4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val selectedStartDateUtc =
                            dateRangePickerState.selectedStartDateMillis?.let {
                                Instant.fromEpochMilliseconds(it)
                            } ?: startDateUtc
                        val selectedEndDateUtc = dateRangePickerState.selectedEndDateMillis?.let {
                            Instant.fromEpochMilliseconds(it)
                        } ?: safeEndDateUtc

                        val selectedStartDate =
                            selectedStartDateUtc - selectedStartDateUtc.getUtcOffsetDuration()
                        val selectedEndDate =
                            selectedEndDateUtc - selectedEndDateUtc.getUtcOffsetDuration()
                        onDismissRequest()
                        onConfirm(selectedStartDate, selectedEndDate)
                    },
                ) {
                    Text("Select")
                }
            }

        }
    }
    DateTimePeriod
}

@Composable
internal fun DateRangeButton(
    startDate: Instant,
    endDate: Instant,
    onClick: () -> Unit,
    dateFormatter: DateTimeFormat<LocalDateTime> = DateFormatters.simpleLocalFormatter,
) {
    val startDateFormatted =
        startDate.toLocalDateTime(TimeZone.currentSystemDefault())
            .format(dateFormatter)
    val endDateFormatted = endDate.toLocalDateTime(TimeZone.currentSystemDefault())
        .format(dateFormatter)

    val textStyle = MaterialTheme.typography.labelMedium
    TextButton(onClick = onClick, contentPadding = PaddingValues(10.dp, 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.DateRange,
                contentDescription = "Date Range",
            )
            Spacer(Modifier.width(6.dp))
            Column {
                Text(startDateFormatted, style = textStyle)
                Text(endDateFormatted, style = textStyle)
            }
        }
    }
}

private fun Instant.getUtcOffsetDuration(): Duration =
    TimeZone.currentSystemDefault().offsetAt(this).totalSeconds.seconds