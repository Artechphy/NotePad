package com.example.android.notepad;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

final class NoteTimestamp {
    private NoteTimestamp() {}

    /** Null means the stored timestamp is missing or invalid. */
    static String format(Long millis, TimeZone timeZone, Locale locale) {
        if (millis == null || millis <= 0) {
            return null;
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", locale);
        format.setTimeZone(timeZone);
        return format.format(new Date(millis));
    }
}
