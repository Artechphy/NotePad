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

import android.app.Activity
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText

/**
 * Allows the user to edit a note's title in a floating dialog window.
 */
class TitleEditor : Activity() {

    private var mCursor: Cursor? = null
    private var mText: EditText? = null
    private var mUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.title_editor)
        mUri = intent?.data

        @Suppress("DEPRECATION")
        mCursor = managedQuery(
            mUri,
            PROJECTION,
            null,
            null,
            null,
        )

        mText = findViewById(R.id.title)
    }

    override fun onResume() {
        super.onResume()

        val cursor = mCursor
        if (cursor != null) {
            cursor.moveToFirst()
            mText?.setText(cursor.getString(COLUMN_INDEX_TITLE))
        }
    }

    override fun onPause() {
        super.onPause()

        if (mCursor != null) {
            val values = ContentValues()
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, mText?.text?.toString() ?: "")
            mUri?.let { uri ->
                contentResolver.update(
                    uri,
                    values,
                    null,
                    null,
                )
            }
        }
    }

    /** Referenced via android:onClick="onClickOk" in res/layout/title_editor.xml. */
    @Suppress("UNUSED_PARAMETER")
    fun onClickOk(v: View?) {
        finish()
    }

    companion object {
        const val EDIT_TITLE_ACTION: String = "com.android.notepad.action.EDIT_TITLE"

        private val PROJECTION = arrayOf(
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_TITLE,
        )

        private const val COLUMN_INDEX_TITLE = 1
    }
}
