/*
 * Copyright (C) 2009 The Android Open Source Project
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

@file:Suppress("DEPRECATION")

package com.example.android.notepad

import android.app.Activity
import android.content.Intent
import android.content.Intent.ShortcutIconResource
import android.os.Bundle
import android.provider.LiveFolders

/**
 * Creates a live folder Intent and sends it back to HOME.
 */
class NotesLiveFolder : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent?.action
        if (LiveFolders.ACTION_CREATE_LIVE_FOLDER == action) {
            val liveFolderIntent = Intent()
            liveFolderIntent.data = NotePad.Notes.LIVE_FOLDER_URI

            val folderName = getString(R.string.live_folder_name)
            liveFolderIntent.putExtra(LiveFolders.EXTRA_LIVE_FOLDER_NAME, folderName)

            val folderIcon = ShortcutIconResource.fromContext(this, R.drawable.live_folder_notes)
            liveFolderIntent.putExtra(LiveFolders.EXTRA_LIVE_FOLDER_ICON, folderIcon)

            liveFolderIntent.putExtra(
                LiveFolders.EXTRA_LIVE_FOLDER_DISPLAY_MODE,
                LiveFolders.DISPLAY_MODE_LIST,
            )

            val returnIntent = Intent(Intent.ACTION_EDIT, NotePad.Notes.CONTENT_ID_URI_PATTERN)
            liveFolderIntent.putExtra(LiveFolders.EXTRA_LIVE_FOLDER_BASE_INTENT, returnIntent)

            setResult(RESULT_OK, liveFolderIntent)
        } else {
            setResult(RESULT_CANCELED)
        }

        finish()
    }
}
