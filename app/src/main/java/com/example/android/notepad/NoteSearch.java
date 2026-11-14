package com.example.android.notepad;

/** Parameterized, literal substring search shared by the list and its tests. */
final class NoteSearch {
    private NoteSearch() {}

    static String normalize(String keyword) {
        return keyword == null ? "" : keyword.trim();
    }

    static String selection(String keyword) {
        return selection(keyword, null);
    }

    static String selection(String keyword, String category) {
        if (category != null) NoteCategory.requireValid(category);
        String text = normalize(keyword).isEmpty() ? null
                : "(" + NotePad.Notes.COLUMN_NAME_TITLE + " LIKE ? ESCAPE '!' OR "
                + NotePad.Notes.COLUMN_NAME_NOTE + " LIKE ? ESCAPE '!')";
        if (category == null) return text;
        return text == null ? "category = ?" : text + " AND category = ?";
    }

    static String[] arguments(String keyword) {
        return arguments(keyword, null);
    }

    static String[] arguments(String keyword, String category) {
        if (category != null) NoteCategory.requireValid(category);
        String query = normalize(keyword);
        if (query.isEmpty()) {
            return category == null ? null : new String[] { category };
        }
        String pattern = "%" + query.replace("!", "!!")
                .replace("%", "!%").replace("_", "!_") + "%";
        return category == null ? new String[] { pattern, pattern }
                : new String[] { pattern, pattern, category };
    }
}
