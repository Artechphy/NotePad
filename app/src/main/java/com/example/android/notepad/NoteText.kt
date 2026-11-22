package com.example.android.notepad

internal object NoteText {
    private val WHITESPACE = Regex("\\s+")

    fun truncate(text: String, maxCodePoints: Int): String {
        val count = text.codePointCount(0, text.length)
        return if (count <= maxCodePoints) {
            text
        } else {
            text.substring(0, text.offsetByCodePoints(0, maxCodePoints))
        }
    }

    fun preview(text: String?): String {
        return if (text == null) {
            ""
        } else {
            truncate(text.replace(WHITESPACE, " ").trim { it <= ' ' }, 160)
        }
    }

    fun title(entered: String, body: String?): String {
        val trimmed = entered.trim { it <= ' ' }
        if (trimmed.isNotEmpty()) {
            return trimmed
        }
        return truncate(preview(body), 40)
    }
}
