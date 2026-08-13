package com.kogen.giraffe.ui.common.presentation.extensions

import java.util.Calendar
import java.util.Date

/** Formats this epoch-millis timestamp as `H:M:S` (unpadded, device local time), used for a message's inline timestamp. */
internal fun Long.timestampToTime(): String {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = this

    return "${calendar.get(Calendar.HOUR_OF_DAY)}:${calendar.get(Calendar.MINUTE)}:${
        calendar.get(Calendar.SECOND)
    }"
}

/** Formats this epoch-millis timestamp with [Date]'s default (platform-locale) representation, used for a call's full start/end time display. */
internal fun Long.timestampToDateTime(): String {
    return Date(this).toString()
}

/** Formats this millisecond duration as `M:SS`, e.g. for a voice message's elapsed/total time. */
internal fun Int.msToDurationText(): String {
    val totalSeconds = this / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}