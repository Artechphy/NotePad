package com.example.android.notepad;

import org.junit.Test;
import static org.junit.Assert.*;

public class NoteSearchTest {
    @Test public void emptyOrWhitespaceRestoresUnfilteredQuery() {
        for (String text : new String[] { null, "", "  \n\t " }) {
            assertNull(NoteSearch.selection(text));
            assertNull(NoteSearch.arguments(text));
        }
    }

    @Test public void bindsBothColumnsWithoutInterpolatingUserInput() {
        assertArrayEquals(new String[] { "%O'Reilly%", "%O'Reilly%" },
                NoteSearch.arguments(" O'Reilly "));
        assertFalse(NoteSearch.selection("' OR 1=1 --").contains("1=1"));
        assertEquals(NoteSearch.selection("a"), NoteSearch.selection("' OR 1=1 --"));
    }

    @Test public void likeMetacharactersAreLiteralAndBackslashIsOrdinary() {
        assertArrayEquals(new String[] { "%50!%!_!!\\%", "%50!%!_!!\\%" },
                NoteSearch.arguments("50%_!\\"));
    }
}
