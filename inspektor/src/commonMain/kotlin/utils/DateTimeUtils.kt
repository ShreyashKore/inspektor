package utils

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

internal fun Instant.atLocalStartOfDay(timeZone: TimeZone = TimeZone.currentSystemDefault()): Instant {
    val localDate = this.toLocalDateTime(timeZone)
    val timeSinceMidnight =
        localDate.hour.hours + localDate.minute.minutes + localDate.second.seconds + localDate.nanosecond.nanoseconds
    return this - timeSinceMidnight
}

internal fun Instant.atLocalEndOfDay(timeZone: TimeZone = TimeZone.currentSystemDefault()): Instant {
    val localDate = this.toLocalDateTime(timeZone)
    val timeTillMidnight =
        1.days - 1.nanoseconds - (localDate.hour.hours + localDate.minute.minutes + localDate.second.seconds + localDate.nanosecond.nanoseconds)
    return this + timeTillMidnight
}

internal object DateTimeFormatters {
    internal val simpleLocalFormatter = LocalDateTime.Format {
        dayOfMonth(); char(' '); monthName(MonthNames.ENGLISH_ABBREVIATED); char(' '); year()
        hour(); char(':'); minute(); char(':'); second()
    }


    internal val simpleFormatter = DateTimeComponents.Format {
        dayOfMonth(); char(' '); monthName(MonthNames.ENGLISH_ABBREVIATED); char(' '); year();
        hour(); char(':'); minute(); char(':'); second()
    }
}


internal object DateFormatters {

    internal val simpleLocalFormatter = LocalDateTime.Format {
        dayOfMonth(); char(' '); monthName(MonthNames.ENGLISH_ABBREVIATED); char(' '); year()
    }
}

internal object TimeFormatters {
    internal val simpleLocalAmPm = LocalDateTime.Format {
        amPmHour(); char(':'); minute();char(':'); second()
        char(' '); amPmMarker("AM", "PM")
    }

}

