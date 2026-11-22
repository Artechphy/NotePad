package com.example.android.notepad

object NoteText {
    private val WHITESPACE = Regex("\\s+")

    @JvmStatic
    fun truncate(text: String, maxCodePoints: Int): String {
        val count = text.codePointCount(0, text.length)
        return if (count <= maxCodePoints) {
            text
        } else {
            text.substring(0, text.offsetByCodePoints(0, maxCodePoints))
        }
    }

    @JvmStatic
    fun preview(text: String?): String {
        return if (text == null) {
            ""
        } else {
            truncate(text.replace(WHITESPACE, " ").trim(), 160)
        }
    }

    @JvmStatic
    fun title(entered: String, body: String?): String {
        val trimmed = entered.trim()
        if (trimmed.isNotEmpty()) {
            return trimmed
        }
        return truncate(preview(body), 40)
    }
}
