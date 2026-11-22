package com.example.android.notepad

/** Parameterized, literal substring search shared by the list and its tests. */
object NoteSearch {
    @JvmStatic
    fun normalize(keyword: String?): String = keyword?.trim() ?: ""

    @JvmStatic
    @JvmOverloads
    fun selection(keyword: String?, category: String? = null): String? {
        if (category != null) {
            NoteCategory.requireValid(category)
        }
        val text = if (normalize(keyword).isEmpty()) {
            null
        } else {
            "(${NotePad.Notes.COLUMN_NAME_TITLE} LIKE ? ESCAPE '!' OR " +
                "${NotePad.Notes.COLUMN_NAME_NOTE} LIKE ? ESCAPE '!')"
        }
        if (category == null) return text
        return if (text == null) "category = ?" else "$text AND category = ?"
    }

    @JvmStatic
    @JvmOverloads
    fun arguments(keyword: String?, category: String? = null): Array<String>? {
        if (category != null) {
            NoteCategory.requireValid(category)
        }
        val query = normalize(keyword)
        if (query.isEmpty()) {
            return if (category == null) null else arrayOf(category)
        }
        val pattern = "%" + query.replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_") + "%"
        return if (category == null) {
            arrayOf(pattern, pattern)
        } else {
            arrayOf(pattern, pattern, category)
        }
    }
}
