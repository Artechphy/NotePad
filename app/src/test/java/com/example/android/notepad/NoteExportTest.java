package com.example.android.notepad;

import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import static org.junit.Assert.*;

public class NoteExportTest {
    @Test public void writesUtf8TitleMetadataAndBodyAndClosesTheStream() throws Exception {
        final boolean[] closed = { false };
        ByteArrayOutputStream stream = new ByteArrayOutputStream() {
            @Override public void close() throws IOException { closed[0] = true; super.close(); }
        };
        NoteExport.write(stream, NoteExport.document("期中笔记", "第一行\n第二行 📘", "学习", "2026-11-07 20:30"));
        assertEquals("期中笔记\n\n分类：学习\n修改时间：2026-11-07 20:30\n\n第一行\n第二行 📘\n",
                stream.toString("UTF-8"));
        assertTrue(closed[0]);
    }

    @Test public void nullWriteAndCloseFailuresAreNotReportedAsSuccess() {
        assertThrows(IOException.class, () -> NoteExport.write(null, "body"));
        assertThrows(IOException.class, () -> NoteExport.write(new OutputStream() {
            @Override public void write(int value) throws IOException { throw new IOException("disk full"); }
        }, "body"));
        assertThrows(IOException.class, () -> NoteExport.write(new ByteArrayOutputStream() {
            @Override public void close() throws IOException { throw new IOException("close failed"); }
        }, "body"));
    }

    @Test public void filenamesHaveNoPathSeparatorsAndPreserveUnicodeBoundaries() {
        assertEquals("课程_复习_计划.txt", NoteExport.filename("课程/复习\\计划"));
        assertEquals("笔记.txt", NoteExport.filename(" ... "));
        assertEquals("笔记.txt", NoteExport.filename("笔记.txt"));
        StringBuilder title = new StringBuilder();
        for (int i = 0; i < 70; i++) title.append("📘");
        String filename = NoteExport.filename(title.toString());
        assertEquals(68, filename.codePointCount(0, filename.length()));
        assertFalse(Character.isHighSurrogate(filename.charAt(filename.length() - 5)));
    }
}
