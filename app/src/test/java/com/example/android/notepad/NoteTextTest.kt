package com.example.android.notepad

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteTextTest {
    @Test
    fun explicitTitleWinsAndMissingTitleUsesBody() {
        assertEquals("自定义标题", NoteText.title(" 自定义标题 ", "正文"))
        assertEquals("第一行 第二行", NoteText.title(" ", "第一行\n第二行"))
        assertEquals("", NoteText.title("", "  \n"))
    }

    @Test
    fun previewCollapsesWhitespaceAndAcceptsMissingBody() {
        assertEquals("", NoteText.preview(null))
        assertEquals("a b c", NoteText.preview("a\n\nb\t c "))
    }

    @Test
    fun truncationPreservesEmojiCodePoints() {
        assertEquals("A📘", NoteText.truncate("A📘B", 2))
        assertEquals("A📘B", NoteText.truncate("A📘B", 3))
    }
}
