package com.example.android.notepad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

class NoteExportTest {
    @Test
    fun writesUtf8TitleMetadataAndBodyAndClosesTheStream() {
        var closed = false
        val stream = object : ByteArrayOutputStream() {
            override fun close() {
                closed = true
                super.close()
            }
        }
        NoteExport.write(
            stream,
            NoteExport.document("期中笔记", "第一行\n第二行 📘", "学习", "2026-11-07 20:30")
        )
        assertEquals(
            "期中笔记\n\n分类：学习\n修改时间：2026-11-07 20:30\n\n第一行\n第二行 📘\n",
            stream.toString("UTF-8")
        )
        assertTrue(closed)
    }

    @Test
    fun nullWriteAndCloseFailuresAreNotReportedAsSuccess() {
        assertThrows(IOException::class.java) { NoteExport.write(null, "body") }
        assertThrows(IOException::class.java) {
            NoteExport.write(object : OutputStream() {
                override fun write(value: Int) {
                    throw IOException("disk full")
                }
            }, "body")
        }
        assertThrows(IOException::class.java) {
            NoteExport.write(object : ByteArrayOutputStream() {
                override fun close() {
                    throw IOException("close failed")
                }
            }, "body")
        }
    }

    @Test
    fun filenamesHaveNoPathSeparatorsAndPreserveUnicodeBoundaries() {
        assertEquals("课程_复习_计划.txt", NoteExport.filename("课程/复习\\计划"))
        assertEquals("笔记.txt", NoteExport.filename(" ... "))
        assertEquals("笔记.txt", NoteExport.filename("笔记.txt"))
        val title = "📘".repeat(70)
        val filename = NoteExport.filename(title)
        assertEquals(68, filename.codePointCount(0, filename.length))
        assertFalse(Character.isHighSurrogate(filename[filename.length - 5]))
    }
}
