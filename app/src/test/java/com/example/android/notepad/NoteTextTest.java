package com.example.android.notepad;

import org.junit.Test;
import static org.junit.Assert.*;

public class NoteTextTest {
    @Test public void explicitTitleWinsAndMissingTitleUsesBody() {
        assertEquals("自定义标题", NoteText.title(" 自定义标题 ", "正文"));
        assertEquals("第一行 第二行", NoteText.title(" ", "第一行\n第二行"));
        assertEquals("", NoteText.title("", "  \n"));
    }

    @Test public void previewCollapsesWhitespaceAndAcceptsMissingBody() {
        assertEquals("", NoteText.preview(null));
        assertEquals("a b c", NoteText.preview("a\n\nb\t c "));
    }

    @Test public void truncationPreservesEmojiCodePoints() {
        assertEquals("A📘", NoteText.truncate("A📘B", 2));
        assertEquals("A📘B", NoteText.truncate("A📘B", 3));
    }
}
