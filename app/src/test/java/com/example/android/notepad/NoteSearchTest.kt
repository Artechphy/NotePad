package com.example.android.notepad

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class NoteSearchTest {
    @Test
    fun emptyOrWhitespaceRestoresUnfilteredQuery() {
        for (text in arrayOf<String?>(null, "", "  \n\t ")) {
            assertNull(NoteSearch.selection(text))
            assertNull(NoteSearch.arguments(text))
        }
    }

    @Test
    fun bindsBothColumnsWithoutInterpolatingUserInput() {
        assertArrayEquals(
            arrayOf("%O'Reilly%", "%O'Reilly%"),
            NoteSearch.arguments(" O'Reilly ")
        )
        assertFalse(NoteSearch.selection("' OR 1=1 --")!!.contains("1=1"))
        assertEquals(NoteSearch.selection("a"), NoteSearch.selection("' OR 1=1 --"))
    }

    @Test
    fun likeMetacharactersAreLiteralAndBackslashIsOrdinary() {
        assertArrayEquals(
            arrayOf("%50!%!_!!\\%", "%50!%!_!!\\%"),
            NoteSearch.arguments("50%_!\\")
        )
    }
}
