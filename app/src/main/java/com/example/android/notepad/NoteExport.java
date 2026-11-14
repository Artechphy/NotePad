package com.example.android.notepad;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

final class NoteExport {
    private NoteExport() {}

    static String filename(String title) {
        String name = title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        name = name.replaceAll("^[. ]+|[. ]+$", "");
        if (name.isEmpty()) name = "笔记";
        name = NoteText.truncate(name, 64);
        return name.endsWith(".txt") ? name : name + ".txt";
    }

    static String document(String title, String body, String category, String time) {
        return title + "\n\n分类：" + category + "\n修改时间：" + time + "\n\n" + body + "\n";
    }

    /** Owns the output stream; closing failures must also be reported as failures. */
    static void write(OutputStream stream, String document) throws IOException {
        if (stream == null) throw new IOException("Document provider returned no output stream");
        try (Writer writer = new OutputStreamWriter(stream, "UTF-8")) {
            writer.write(document);
        }
    }
}
