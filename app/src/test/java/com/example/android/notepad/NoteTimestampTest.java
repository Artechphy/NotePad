package com.example.android.notepad;

import org.junit.Test;
import java.util.Locale;
import java.util.TimeZone;
import static org.junit.Assert.*;

public class NoteTimestampTest {
    @Test public void formatsLocalDateAcrossMidnight() {
        // 2024-01-01 20:04 UTC is the next day in Shanghai.
        assertEquals("2024-01-02 04:04", NoteTimestamp.format(1704139440000L,
                TimeZone.getTimeZone("Asia/Shanghai"), Locale.CHINA));
        assertEquals("2024-01-01 20:04", NoteTimestamp.format(1704139440000L,
                TimeZone.getTimeZone("UTC"), Locale.US));
    }

    @Test public void missingOrInvalidTimeHasNoFabricatedDate() {
        assertNull(NoteTimestamp.format(null, TimeZone.getTimeZone("UTC"), Locale.US));
        assertNull(NoteTimestamp.format(0L, TimeZone.getTimeZone("UTC"), Locale.US));
        assertNull(NoteTimestamp.format(-1L, TimeZone.getTimeZone("UTC"), Locale.US));
    }
}
