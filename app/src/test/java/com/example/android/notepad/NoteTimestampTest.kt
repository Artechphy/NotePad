package com.example.android.notepad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class NoteTimestampTest {
    @Test
    fun formatsLocalDateAcrossMidnight() {
        // 2024-01-01 20:04 UTC is the next day in Shanghai.
        assertEquals(
            "2024-01-02 04:04",
            NoteTimestamp.format(1704139440000L, TimeZone.getTimeZone("Asia/Shanghai"), Locale.CHINA)
        )
        assertEquals(
            "2024-01-01 20:04",
            NoteTimestamp.format(1704139440000L, TimeZone.getTimeZone("UTC"), Locale.US)
        )
    }

    @Test
    fun missingOrInvalidTimeHasNoFabricatedDate() {
        assertNull(NoteTimestamp.format(null, TimeZone.getTimeZone("UTC"), Locale.US))
        assertNull(NoteTimestamp.format(0L, TimeZone.getTimeZone("UTC"), Locale.US))
        assertNull(NoteTimestamp.format(-1L, TimeZone.getTimeZone("UTC"), Locale.US))
    }
}
