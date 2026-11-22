package com.example.android.notepad

import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter

object NoteExport {
    private val INVALID_FILENAME_CHARS = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
    private val EDGE_DOTS_OR_SPACES = Regex("^[. ]+|[. ]+$")

    @JvmStatic
    fun filename(title: String): String {
        var name = title.replace(INVALID_FILENAME_CHARS, "_").trim { it <= ' ' }
        name = name.replace(EDGE_DOTS_OR_SPACES, "")
        if (name.isEmpty()) name = "笔记"
        name = NoteText.truncate(name, 64)
        return if (name.endsWith(".txt")) name else "$name.txt"
    }

    @JvmStatic
    fun document(title: String?, body: String?, category: String?, time: String?): String {
        return "$title\n\n分类：$category\n修改时间：$time\n\n$body\n"
    }

    /** Owns the output stream; closing failures must also be reported as failures. */
    @JvmStatic
    @Throws(IOException::class)
    fun write(stream: OutputStream?, document: String) {
        if (stream == null) {
            throw IOException("Document provider returned no output stream")
        }
        OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
            writer.write(document)
        }
    }
}
