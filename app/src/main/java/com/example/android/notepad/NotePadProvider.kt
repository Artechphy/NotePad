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

import android.content.ClipDescription
import android.content.ContentProvider
import android.content.ContentProvider.PipeDataWriter
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.content.res.Resources
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteQueryBuilder
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.LiveFolders
import android.text.TextUtils
import android.util.Log
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.UnsupportedEncodingException

/**
 * Provides access to a database of notes. Each note has a title, the note
 * itself, a creation date and a modified date.
 */
class NotePadProvider : ContentProvider(), PipeDataWriter<Cursor> {
    private lateinit var mOpenHelper: DatabaseHelper

    /**
     * Helps open, create, and upgrade the database file. Kept accessible for tests.
     */
    class DatabaseHelper(context: Context?) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE ${NotePad.Notes.TABLE_NAME} (" +
                    "${NotePad.Notes._ID} INTEGER PRIMARY KEY," +
                    "${NotePad.Notes.COLUMN_NAME_TITLE} TEXT," +
                    "${NotePad.Notes.COLUMN_NAME_NOTE} TEXT," +
                    "${NotePad.Notes.COLUMN_NAME_CREATE_DATE} INTEGER," +
                    "${NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE} INTEGER," +
                    "${NotePad.Notes.COLUMN_NAME_CATEGORY} TEXT NOT NULL DEFAULT 'uncategorized'" +
                    ");",
            )
        }

        /** Upgrade the known v2 schema in place; unknown versions must never be erased. */
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion == 2 && newVersion == 3) {
                db.execSQL(
                    "ALTER TABLE notes ADD COLUMN category" +
                        " TEXT NOT NULL DEFAULT 'uncategorized'",
                )
            } else {
                throw SQLException("Unsupported database upgrade: $oldVersion -> $newVersion")
            }
        }
    }

    override fun onCreate(): Boolean {
        mOpenHelper = DatabaseHelper(context)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? {
        val qb = SQLiteQueryBuilder()
        qb.tables = NotePad.Notes.TABLE_NAME

        when (sUriMatcher.match(uri)) {
            NOTES -> qb.projectionMap = sNotesProjectionMap
            NOTE_ID -> {
                qb.projectionMap = sNotesProjectionMap
                qb.appendWhere(
                    "${NotePad.Notes._ID}=${uri.pathSegments[NotePad.Notes.NOTE_ID_PATH_POSITION]}",
                )
            }
            LIVE_FOLDER_NOTES -> qb.projectionMap = sLiveFolderProjectionMap
            else -> throw IllegalArgumentException("Unknown URI $uri")
        }

        val orderBy = if (TextUtils.isEmpty(sortOrder)) {
            NotePad.Notes.DEFAULT_SORT_ORDER
        } else {
            sortOrder
        }

        val db = mOpenHelper.readableDatabase
        val c = qb.query(
            db,
            projection,
            selection,
            selectionArgs,
            null,
            null,
            orderBy,
        )

        context?.contentResolver?.let { resolver ->
            c?.setNotificationUri(resolver, uri)
        }
        return c
    }

    override fun getType(uri: Uri): String {
        return when (sUriMatcher.match(uri)) {
            NOTES, LIVE_FOLDER_NOTES -> NotePad.Notes.CONTENT_TYPE
            NOTE_ID -> NotePad.Notes.CONTENT_ITEM_TYPE
            else -> throw IllegalArgumentException("Unknown URI $uri")
        }
    }

    override fun getStreamTypes(uri: Uri, mimeTypeFilter: String): Array<String>? {
        return when (sUriMatcher.match(uri)) {
            NOTES, LIVE_FOLDER_NOTES -> null
            NOTE_ID -> NOTE_STREAM_TYPES.filterMimeTypes(mimeTypeFilter)
            else -> throw IllegalArgumentException("Unknown URI $uri")
        }
    }

    @Throws(FileNotFoundException::class)
    override fun openTypedAssetFile(
        uri: Uri,
        mimeTypeFilter: String,
        opts: Bundle?,
    ): AssetFileDescriptor? {
        val mimeTypes = getStreamTypes(uri, mimeTypeFilter)
        if (mimeTypes != null) {
            val c = query(
                uri,
                READ_NOTE_PROJECTION,
                null,
                null,
                null,
            )

            if (c == null || !c.moveToFirst()) {
                c?.close()
                throw FileNotFoundException("Unable to query $uri")
            }

            return AssetFileDescriptor(
                openPipeHelper(uri, mimeTypes[0], opts, c, this),
                0,
                AssetFileDescriptor.UNKNOWN_LENGTH,
            )
        }

        return super.openTypedAssetFile(uri, mimeTypeFilter, opts)
    }

    override fun writeDataToPipe(
        output: ParcelFileDescriptor,
        uri: Uri,
        mimeType: String,
        opts: Bundle?,
        c: Cursor?,
    ) {
        val fout = FileOutputStream(output.fileDescriptor)
        var pw: PrintWriter? = null
        try {
            pw = PrintWriter(OutputStreamWriter(fout, "UTF-8"))
            pw.println(c?.getString(READ_NOTE_TITLE_INDEX))
            pw.println("")
            pw.println(c?.getString(READ_NOTE_NOTE_INDEX))
        } catch (e: UnsupportedEncodingException) {
            Log.w(TAG, "Ooops", e)
        } finally {
            c?.close()
            pw?.flush()
            try {
                fout.close()
            } catch (_: IOException) {
            }
        }
    }

    override fun insert(uri: Uri, initialValues: ContentValues?): Uri {
        if (sUriMatcher.match(uri) != NOTES) {
            throw IllegalArgumentException("Unknown URI $uri")
        }

        val values = if (initialValues != null) {
            ContentValues(initialValues)
        } else {
            ContentValues()
        }

        if (values.containsKey(NotePad.Notes.COLUMN_NAME_CATEGORY)) {
            NoteCategory.requireValid(values.getAsString(NotePad.Notes.COLUMN_NAME_CATEGORY))
        }
        val now = System.currentTimeMillis()

        if (!values.containsKey(NotePad.Notes.COLUMN_NAME_CREATE_DATE)) {
            values.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE, now)
        }

        if (!values.containsKey(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE)) {
            values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, now)
        }

        if (!values.containsKey(NotePad.Notes.COLUMN_NAME_TITLE)) {
            val r = Resources.getSystem()
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, r.getString(android.R.string.untitled))
        }

        if (!values.containsKey(NotePad.Notes.COLUMN_NAME_NOTE)) {
            values.put(NotePad.Notes.COLUMN_NAME_NOTE, "")
        }

        val db = mOpenHelper.writableDatabase
        val rowId = db.insert(
            NotePad.Notes.TABLE_NAME,
            NotePad.Notes.COLUMN_NAME_NOTE,
            values,
        )

        if (rowId > 0) {
            val noteUri = ContentUris.withAppendedId(NotePad.Notes.CONTENT_ID_URI_BASE, rowId)
            context?.contentResolver?.notifyChange(noteUri, null)
            return noteUri
        }

        throw SQLException("Failed to insert row into $uri")
    }

    override fun delete(uri: Uri, where: String?, whereArgs: Array<String>?): Int {
        val db = mOpenHelper.writableDatabase
        val count = when (sUriMatcher.match(uri)) {
            NOTES -> db.delete(NotePad.Notes.TABLE_NAME, where, whereArgs)
            NOTE_ID -> {
                var finalWhere =
                    "${NotePad.Notes._ID} = ${uri.pathSegments[NotePad.Notes.NOTE_ID_PATH_POSITION]}"
                if (where != null) {
                    finalWhere = "$finalWhere AND $where"
                }
                db.delete(NotePad.Notes.TABLE_NAME, finalWhere, whereArgs)
            }
            else -> throw IllegalArgumentException("Unknown URI $uri")
        }

        context?.contentResolver?.notifyChange(uri, null)
        return count
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        where: String?,
        whereArgs: Array<String>?,
    ): Int {
        val incomingValues = values ?: ContentValues()
        if (incomingValues.containsKey(NotePad.Notes.COLUMN_NAME_CATEGORY)) {
            NoteCategory.requireValid(incomingValues.getAsString(NotePad.Notes.COLUMN_NAME_CATEGORY))
        }

        // Preserve callers that explicitly supply historical timestamps. Normal editor writes
        // omit modified: only rows whose title/body/category actually changes receive a new timestamp.
        var effectiveWhere = where
        var effectiveWhereArgs: Array<String?>? = whereArgs?.let { Array(it.size) { i -> it[i] } }
        val updatedValues = ContentValues(incomingValues)
        if (!incomingValues.containsKey(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE)) {
            val differences = ArrayList<String>()
            val arguments = ArrayList<String?>()
            if (whereArgs != null) {
                arguments.addAll(whereArgs)
            }
            val editableColumns = arrayOf(
                NotePad.Notes.COLUMN_NAME_TITLE,
                NotePad.Notes.COLUMN_NAME_NOTE,
                NotePad.Notes.COLUMN_NAME_CATEGORY,
            )
            for (column in editableColumns) {
                if (incomingValues.containsKey(column)) {
                    differences.add("$column IS NOT ?")
                    arguments.add(incomingValues.getAsString(column))
                }
            }
            if (differences.isNotEmpty()) {
                val changed = "(${TextUtils.join(" OR ", differences)})"
                effectiveWhere = if (TextUtils.isEmpty(effectiveWhere)) {
                    changed
                } else {
                    "($effectiveWhere) AND $changed"
                }
                effectiveWhereArgs = arguments.toTypedArray()
                updatedValues.put(
                    NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
                    System.currentTimeMillis(),
                )
            }
        }

        val db = mOpenHelper.writableDatabase
        val count = when (sUriMatcher.match(uri)) {
            NOTES -> db.update(
                NotePad.Notes.TABLE_NAME,
                updatedValues,
                effectiveWhere,
                effectiveWhereArgs,
            )
            NOTE_ID -> {
                var finalWhere =
                    "${NotePad.Notes._ID} = ${uri.pathSegments[NotePad.Notes.NOTE_ID_PATH_POSITION]}"
                if (effectiveWhere != null) {
                    finalWhere = "$finalWhere AND ($effectiveWhere)"
                }
                db.update(
                    NotePad.Notes.TABLE_NAME,
                    updatedValues,
                    finalWhere,
                    effectiveWhereArgs,
                )
            }
            else -> throw IllegalArgumentException("Unknown URI $uri")
        }

        if (count > 0) {
            context?.contentResolver?.notifyChange(uri, null)
        }
        return count
    }

    /**
     * Test hook for accessing the underlying [DatabaseHelper] without Kotlin internal name mangling.
     */
    fun getOpenHelperForTest(): DatabaseHelper = mOpenHelper

    companion object {
        private const val TAG = "NotePadProvider"
        private const val DATABASE_NAME = "note_pad.db"
        private const val DATABASE_VERSION = 3

        private val sNotesProjectionMap = HashMap<String, String>().apply {
            put(NotePad.Notes._ID, NotePad.Notes._ID)
            put(NotePad.Notes.COLUMN_NAME_TITLE, NotePad.Notes.COLUMN_NAME_TITLE)
            put(NotePad.Notes.COLUMN_NAME_NOTE, NotePad.Notes.COLUMN_NAME_NOTE)
            put(NotePad.Notes.COLUMN_NAME_CATEGORY, NotePad.Notes.COLUMN_NAME_CATEGORY)
            put(NotePad.Notes.COLUMN_NAME_CREATE_DATE, NotePad.Notes.COLUMN_NAME_CREATE_DATE)
            put(
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
            )
        }

        @Suppress("DEPRECATION")
        private val sLiveFolderProjectionMap = HashMap<String, String>().apply {
            put(LiveFolders._ID, "${NotePad.Notes._ID} AS ${LiveFolders._ID}")
            put(LiveFolders.NAME, "${NotePad.Notes.COLUMN_NAME_TITLE} AS ${LiveFolders.NAME}")
        }

        private val READ_NOTE_PROJECTION = arrayOf(
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_NOTE,
            NotePad.Notes.COLUMN_NAME_TITLE,
        )
        private const val READ_NOTE_NOTE_INDEX = 1
        private const val READ_NOTE_TITLE_INDEX = 2

        private const val NOTES = 1
        private const val NOTE_ID = 2
        private const val LIVE_FOLDER_NOTES = 3

        private val sUriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(NotePad.AUTHORITY, "notes", NOTES)
            addURI(NotePad.AUTHORITY, "notes/#", NOTE_ID)
            addURI(NotePad.AUTHORITY, "live_folders/notes", LIVE_FOLDER_NOTES)
        }

        @JvmField
        val NOTE_STREAM_TYPES: ClipDescription = ClipDescription(
            null,
            arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN),
        )
    }
}
