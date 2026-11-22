package com.example.android.notepad

import android.content.Context

/** Stable storage IDs; translated labels are presentation only. */
object NoteCategory {
    const val DEFAULT: String = "uncategorized"

    private val IDS = arrayOf(DEFAULT, "study", "life", "work")
    private val LABELS = intArrayOf(
        R.string.category_uncategorized,
        R.string.category_study,
        R.string.category_life,
        R.string.category_work,
    )

    @JvmStatic
    fun indexOf(id: String?): Int {
        for (i in IDS.indices) {
            if (IDS[i] == id) return i
        }
        return -1
    }

    @JvmStatic
    fun requireValid(id: String?) {
        if (indexOf(id) < 0) {
            throw IllegalArgumentException("Unknown note category: $id")
        }
    }

    @JvmStatic
    fun idAt(position: Int): String = IDS[position]

    @JvmStatic
    fun label(context: Context, id: String?): String {
        val index = indexOf(id)
        return context.getString(LABELS[if (index < 0) 0 else index])
    }

    @JvmStatic
    fun labels(context: Context): Array<String> =
        Array(IDS.size) { i -> context.getString(LABELS[i]) }
}
