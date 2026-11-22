package com.example.android.notepad

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object NoteTimestamp {
    /** Null means the stored timestamp is missing or invalid. */
    @JvmStatic
    fun format(millis: Long?, timeZone: TimeZone, locale: Locale): String? {
        if (millis == null || millis <= 0L) {
            return null
        }
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", locale)
        format.timeZone = timeZone
        return format.format(Date(millis))
    }
}
