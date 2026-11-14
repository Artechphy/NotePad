package com.example.android.notepad;

final class NoteText {
    private NoteText() {}

    static String truncate(String text, int maxCodePoints) {
        int count = text.codePointCount(0, text.length());
        return count <= maxCodePoints ? text
                : text.substring(0, text.offsetByCodePoints(0, maxCodePoints));
    }

    static String preview(String text) {
        return text == null ? "" : truncate(text.replaceAll("\\s+", " ").trim(), 160);
    }

    static String title(String entered, String body) {
        if (!entered.trim().isEmpty()) return entered.trim();
        return truncate(preview(body), 40);
    }
}
