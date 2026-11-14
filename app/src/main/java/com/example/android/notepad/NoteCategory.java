package com.example.android.notepad;

import android.content.Context;

/** Stable storage IDs; translated labels are presentation only. */
final class NoteCategory {
    static final String DEFAULT = "uncategorized";
    private static final String[] IDS = { DEFAULT, "study", "life", "work" };
    private static final int[] LABELS = { R.string.category_uncategorized,
            R.string.category_study, R.string.category_life, R.string.category_work };

    private NoteCategory() {}

    static int indexOf(String id) {
        for (int i = 0; i < IDS.length; i++) {
            if (IDS[i].equals(id)) return i;
        }
        return -1;
    }

    static void requireValid(String id) {
        if (indexOf(id) < 0) throw new IllegalArgumentException("Unknown note category: " + id);
    }

    static String idAt(int position) {
        return IDS[position];
    }

    static String label(Context context, String id) {
        int index = indexOf(id);
        return context.getString(LABELS[index < 0 ? 0 : index]);
    }

    static String[] labels(Context context) {
        String[] result = new String[IDS.length];
        for (int i = 0; i < IDS.length; i++) result[i] = context.getString(LABELS[i]);
        return result;
    }
}
