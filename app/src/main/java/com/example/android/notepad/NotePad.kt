/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.notepad

import android.net.Uri
import android.provider.BaseColumns

/**
 * Defines a contract between the Note Pad content provider and its clients.
 */
object NotePad {
    const val AUTHORITY: String = "com.google.provider.NotePad"

    /**
     * Notes table contract
     */
    object Notes : BaseColumns {
        const val _ID: String = BaseColumns._ID
        const val _COUNT: String = BaseColumns._COUNT

        /**
         * The table name offered by this provider
         */
        const val TABLE_NAME: String = "notes"

        /*
         * URI definitions
         */

        private const val SCHEME: String = "content://"
        private const val PATH_NOTES: String = "/notes"
        private const val PATH_NOTE_ID: String = "/notes/"

        /**
         * 0-relative position of a note ID segment in the path part of a note ID URI
         */
        const val NOTE_ID_PATH_POSITION: Int = 1

        private const val PATH_LIVE_FOLDER: String = "/live_folders/notes"

        /**
         * The content:// style URL for this table
         */
        @JvmField
        val CONTENT_URI: Uri = Uri.parse(SCHEME + AUTHORITY + PATH_NOTES)

        /**
         * The content URI base for a single note. Callers must
         * append a numeric note id to this Uri to retrieve a note
         */
        @JvmField
        val CONTENT_ID_URI_BASE: Uri = Uri.parse(SCHEME + AUTHORITY + PATH_NOTE_ID)

        /**
         * The content URI match pattern for a single note, specified by its ID.
         */
        @JvmField
        val CONTENT_ID_URI_PATTERN: Uri = Uri.parse("$SCHEME$AUTHORITY$PATH_NOTE_ID/#")

        /**
         * The content Uri pattern for a notes listing for live folders
         */
        @JvmField
        val LIVE_FOLDER_URI: Uri = Uri.parse(SCHEME + AUTHORITY + PATH_LIVE_FOLDER)

        /*
         * MIME type definitions
         */

        /**
         * The MIME type of [CONTENT_URI] providing a directory of notes.
         */
        const val CONTENT_TYPE: String = "vnd.android.cursor.dir/vnd.google.note"

        /**
         * The MIME type of a [CONTENT_URI] sub-directory of a single note.
         */
        const val CONTENT_ITEM_TYPE: String = "vnd.android.cursor.item/vnd.google.note"

        /**
         * The default sort order for this table
         */
        const val DEFAULT_SORT_ORDER: String = "modified DESC"

        /*
         * Column definitions
         */

        const val COLUMN_NAME_TITLE: String = "title"
        const val COLUMN_NAME_NOTE: String = "note"
        const val COLUMN_NAME_CREATE_DATE: String = "created"
        const val COLUMN_NAME_MODIFICATION_DATE: String = "modified"

        /** Single category, stored as a stable ID rather than a translated label. */
        const val COLUMN_NAME_CATEGORY: String = "category"
    }
}
