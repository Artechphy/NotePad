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

@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package com.example.android.notepad

import android.annotation.TargetApi
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import java.util.Locale
import java.util.TimeZone

/** One editor for title, body and category. Leaving it saves actual changes. */
class NoteEditor : Activity() {
    private var mUri: Uri? = null
    private lateinit var mTitle: EditText
    private lateinit var mText: EditText
    private lateinit var mCategory: Spinner
    private lateinit var mStatus: TextView
    private var mInitialized: Boolean = false
    private var mBinding: Boolean = false
    private var mNewNote: Boolean = false
    private var mDeleted: Boolean = false
    private var mPastePending: Boolean = false
    private var mSaveFailed: Boolean = false
    private var mUnregisterBack: Runnable? = null
    private var mOriginalTitle: String = ""
    private var mOriginalBody: String = ""
    private var mOriginalCategory: String = ""
    private var mSavedTitle: String = ""
    private var mSavedBody: String = ""
    private var mSavedCategory: String = ""
    private var mExportTask: NoteExportTask? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.note_editor)
        mTitle = findViewById(R.id.title)
        mText = findViewById(R.id.note)
        mCategory = findViewById(R.id.editor_category)
        mStatus = findViewById(R.id.save_status)
        val categories = ArrayAdapter(
            this,
            R.layout.spinner_category_item,
            NoteCategory.labels(this)
        )
        categories.setDropDownViewResource(R.layout.spinner_category_dropdown)
        mCategory.adapter = categories

        try {
            val savedUri = state?.getString("note_uri")
            if (savedUri != null) {
                mUri = Uri.parse(savedUri)
                mNewNote = state.getBoolean("new_note")
            } else {
                val action = intent.action
                if (Intent.ACTION_EDIT == action || Intent.ACTION_VIEW == action) {
                    mUri = intent.data
                } else if (Intent.ACTION_INSERT == action || Intent.ACTION_PASTE == action) {
                    val values = ContentValues()
                    values.put(NotePad.Notes.COLUMN_NAME_TITLE, "")
                    val category = intent.getStringExtra(NotePad.Notes.COLUMN_NAME_CATEGORY)
                    values.put(
                        NotePad.Notes.COLUMN_NAME_CATEGORY,
                        if (NoteCategory.indexOf(category) >= 0) category else NoteCategory.DEFAULT
                    )
                    val targetUri = intent.data ?: throw IllegalArgumentException("No note URI")
                    mUri = contentResolver.insert(targetUri, values)
                    mNewNote = true
                } else {
                    throw IllegalArgumentException("Unsupported editor action")
                }
            }
            val noteUri = mUri ?: throw IllegalArgumentException("No note URI")
            try {
                contentResolver.query(noteUri, null, null, null, null)?.useCompat { row ->
                    if (!row.moveToFirst()) throw IllegalArgumentException("Note not found")
                    mSavedTitle = string(row, NotePad.Notes.COLUMN_NAME_TITLE)
                    mSavedBody = string(row, NotePad.Notes.COLUMN_NAME_NOTE)
                    mSavedCategory = string(row, NotePad.Notes.COLUMN_NAME_CATEGORY)
                    mSaveFailed = state != null && state.getBoolean("save_failed")
                } ?: throw IllegalArgumentException("Note not found")
            } catch (error: RuntimeException) {
                // A failed save must not make configuration/process recreation discard input.
                // Keep the original URI for retry; never recreate a removed note implicitly.
                if (state == null || !state.containsKey("draft_title")) throw error
                Log.w("NoteEditor", "Restoring input while note is unavailable", error)
                mSavedTitle = stateString(state, "saved_title", "")
                mSavedBody = stateString(state, "saved_body", "")
                mSavedCategory = stateString(state, "saved_category", NoteCategory.DEFAULT)
                mSaveFailed = true
            }
            mOriginalTitle = if (state == null) mSavedTitle else stateString(state, "original_title", mSavedTitle)
            mOriginalBody = if (state == null) mSavedBody else stateString(state, "original_body", mSavedBody)
            mOriginalCategory = if (state == null) mSavedCategory else stateString(state, "original_category", mSavedCategory)
            mBinding = true
            mTitle.setText(if (state == null) mSavedTitle else stateString(state, "draft_title", mSavedTitle))
            mText.setText(if (state == null) mSavedBody else stateString(state, "draft_body", mSavedBody))
            val category = if (state == null) mSavedCategory else stateString(state, "draft_category", mSavedCategory)
            mCategory.setSelection(maxOf(0, NoteCategory.indexOf(category)))
            mBinding = false
            mInitialized = true
            setResult(RESULT_OK, Intent().setData(noteUri))
        } catch (error: RuntimeException) {
            Log.e("NoteEditor", "Unable to open note", error)
            Toast.makeText(this, R.string.error_message, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateStatus()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        mTitle.addTextChangedListener(watcher)
        mText.addTextChangedListener(watcher)
        mCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateStatus()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        findViewById<View>(R.id.editor_back).setOnClickListener { leaveEditor() }
        findViewById<View>(R.id.editor_save).setOnClickListener { saveAndFinish() }
        findViewById<View>(R.id.editor_more).setOnClickListener { view -> showActions(view) }
        updateStatus()
        val retained = lastNonConfigurationInstance
        if (retained is NoteExportTask) {
            mExportTask = retained
            retained.attach(this)
        }
        mPastePending = if (state == null) {
            Intent.ACTION_PASTE == intent.action
        } else {
            state.getBoolean("paste_pending")
        }
        if (Build.VERSION.SDK_INT >= 33) mUnregisterBack = BackNavigation.register(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Android restricts clipboard reads to the focused app. onCreate/onResume are too
        // early during an Activity transition. Consume once so later focus cannot overwrite edits.
        if (hasFocus && mInitialized && mPastePending && !mDeleted && !isFinishing) {
            mPastePending = false
            pasteNote()
        }
    }

    private fun category(): String {
        return NoteCategory.idAt(maxOf(0, mCategory.selectedItemPosition))
    }

    private fun isEmpty(): Boolean {
        return mTitle.text.toString().trim { it <= ' ' }.isEmpty() &&
            mText.text.toString().trim { it <= ' ' }.isEmpty()
    }

    private fun setStatusBadge(textRes: Int, bgRes: Int, colorRes: Int) {
        mStatus.setText(textRes)
        mStatus.setBackgroundResource(bgRes)
        mStatus.setTextColor(resources.getColor(colorRes))
    }

    private fun updateStatus() {
        if (!mInitialized || mBinding) return
        val changed = mTitle.text.toString() != mSavedTitle ||
            mText.text.toString() != mSavedBody ||
            category() != mSavedCategory
        when {
            mSaveFailed -> setStatusBadge(
                R.string.save_failed,
                R.drawable.status_badge_error,
                R.color.status_error_text
            )
            changed -> setStatusBadge(
                R.string.editing,
                R.drawable.status_badge_editing,
                R.color.status_editing_text
            )
            mNewNote && isEmpty() -> setStatusBadge(
                R.string.new_draft,
                R.drawable.status_badge_neutral,
                R.color.status_neutral_text
            )
            else -> setStatusBadge(
                R.string.saved,
                R.drawable.status_badge_saved,
                R.color.status_saved_text
            )
        }
    }

    private fun saveNote(notify: Boolean): Boolean {
        if (!mInitialized || mDeleted) return false
        if (mNewNote && isEmpty()) {
            if (notify) Toast.makeText(this, R.string.nothing_to_save, Toast.LENGTH_SHORT).show()
            return false
        }
        val body = mText.text.toString()
        var title = NoteText.title(mTitle.text.toString(), body)
        if (title.isEmpty()) title = getString(R.string.untitled_note)
        val values = ContentValues()
        values.put(NotePad.Notes.COLUMN_NAME_TITLE, title)
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, body)
        values.put(NotePad.Notes.COLUMN_NAME_CATEGORY, category())
        return try {
            val noteUri = mUri ?: throw IllegalStateException("Note URI missing")
            // A zero update count also means an unchanged note. Verify existence separately.
            contentResolver.query(
                noteUri,
                arrayOf(NotePad.Notes._ID),
                null,
                null,
                null
            )?.useCompat { row ->
                if (!row.moveToFirst()) throw IllegalStateException("Note was removed")
            } ?: throw IllegalStateException("Note was removed")
            contentResolver.update(noteUri, values, null, null)
            mSaveFailed = false
            mSavedTitle = title
            mSavedBody = body
            mSavedCategory = category()
            mBinding = true
            if (mTitle.text.toString() != title) mTitle.setText(title)
            mBinding = false
            setStatusBadge(R.string.saved, R.drawable.status_badge_saved, R.color.status_saved_text)
            true
        } catch (error: RuntimeException) {
            Log.e("NoteEditor", "Unable to save note", error)
            mSaveFailed = true
            setStatusBadge(
                R.string.save_failed,
                R.drawable.status_badge_error,
                R.color.status_error_text
            )
            if (notify) Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun saveAndFinish() {
        if (saveNote(true)) finish()
    }

    private fun leaveEditor() {
        if (!mInitialized || mDeleted) return
        if (mNewNote && isEmpty()) {
            if (removeNote()) finish()
        } else {
            saveAndFinish()
        }
    }

    override fun onBackPressed() {
        leaveEditor()
    }

    override fun onPause() {
        super.onPause()
        if (!mInitialized || mDeleted) return
        if (isFinishing && mNewNote && isEmpty()) {
            removeNote()
        } else if (!mNewNote || !isEmpty()) {
            if (!saveNote(false)) {
                Toast.makeText(this, R.string.autosave_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onSaveInstanceState(state: Bundle) {
        if (mInitialized && !mDeleted) {
            saveNote(false)
            mUri?.let { state.putString("note_uri", it.toString()) }
            state.putBoolean("new_note", mNewNote)
            state.putBoolean("paste_pending", mPastePending)
            state.putString("original_title", mOriginalTitle)
            state.putString("original_body", mOriginalBody)
            state.putString("original_category", mOriginalCategory)
            state.putString("draft_title", mTitle.text.toString())
            state.putString("draft_body", mText.text.toString())
            state.putString("draft_category", category())
            state.putString("saved_title", mSavedTitle)
            state.putString("saved_body", mSavedBody)
            state.putString("saved_category", mSavedCategory)
            state.putBoolean("save_failed", mSaveFailed)
        }
        super.onSaveInstanceState(state)
    }

    private fun showActions(anchor: View) {
        val popup = PopupMenu(this, anchor)
        onCreateOptionsMenu(popup.menu)
        onPrepareOptionsMenu(popup.menu)
        popup.setOnMenuItemClickListener { item -> onOptionsItemSelected(item) }
        popup.show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.editor_options_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if (!mInitialized) return false
        val changed = mTitle.text.toString() != mOriginalTitle ||
            mText.text.toString() != mOriginalBody ||
            category() != mOriginalCategory
        menu.findItem(R.id.menu_revert)?.isVisible = changed
        menu.findItem(R.id.menu_export)?.isEnabled = Build.VERSION.SDK_INT >= 19
        menu.findItem(R.id.menu_save_as_new)?.isVisible = mSaveFailed && !isEmpty()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_save -> saveAndFinish()
            R.id.menu_save_as_new -> saveAsNewNote()
            R.id.menu_export -> exportNote()
            R.id.menu_delete -> {
                AlertDialog.Builder(this)
                    .setMessage(R.string.delete_confirm)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.menu_delete) { _, _ ->
                        if (removeNote()) finish()
                    }
                    .show()
            }
            R.id.menu_revert -> revertNote()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun removeNote(): Boolean {
        return try {
            val noteUri = mUri ?: throw IllegalStateException("Note URI missing")
            contentResolver.delete(noteUri, null, null)
            mDeleted = true
            setResult(RESULT_CANCELED)
            true
        } catch (error: RuntimeException) {
            Log.e("NoteEditor", "Unable to delete note", error)
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun saveAsNewNote() {
        if (!mSaveFailed || isEmpty()) return
        val body = mText.text.toString()
        val title = NoteText.title(mTitle.text.toString(), body)
        val values = ContentValues()
        values.put(
            NotePad.Notes.COLUMN_NAME_TITLE,
            if (title.isEmpty()) getString(R.string.untitled_note) else title
        )
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, body)
        values.put(NotePad.Notes.COLUMN_NAME_CATEGORY, category())
        try {
            val newUri = contentResolver.insert(NotePad.Notes.CONTENT_URI, values)
                ?: throw IllegalStateException("Unable to create recovery note")
            mUri = newUri
            mNewNote = false
            mSaveFailed = false
            setResult(RESULT_OK, Intent().setData(mUri))
            finish()
        } catch (error: RuntimeException) {
            Log.e("NoteEditor", "Unable to save recovery note", error)
            setStatusBadge(
                R.string.save_failed,
                R.drawable.status_badge_error,
                R.color.status_error_text
            )
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun revertNote() {
        if (mNewNote) {
            if (removeNote()) finish()
        } else {
            mBinding = true
            mTitle.setText(mOriginalTitle)
            mText.setText(mOriginalBody)
            mCategory.setSelection(maxOf(0, NoteCategory.indexOf(mOriginalCategory)))
            mBinding = false
            if (saveNote(true)) finish()
        }
    }

    private fun pasteNote() {
        try {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = clipboard?.primaryClip
            if (clip == null || clip.itemCount == 0) {
                Toast.makeText(this, R.string.paste_empty, Toast.LENGTH_LONG).show()
                return
            }
            val item = clip.getItemAt(0)
            val itemUri = item.uri
            if (itemUri != null && NotePad.AUTHORITY == itemUri.authority) {
                // SQLite can reuse the last deleted row ID for this new draft. A stale
                // clipboard URI must not be mistaken for a readable source note in that case.
                if (itemUri == mUri) {
                    Toast.makeText(this, R.string.paste_source_missing, Toast.LENGTH_LONG).show()
                    return
                }
                contentResolver.query(itemUri, null, null, null, null)?.useCompat { row ->
                    if (!row.moveToFirst()) {
                        Toast.makeText(this, R.string.paste_source_missing, Toast.LENGTH_LONG).show()
                        return
                    }
                    mTitle.setText(string(row, NotePad.Notes.COLUMN_NAME_TITLE))
                    mText.setText(string(row, NotePad.Notes.COLUMN_NAME_NOTE))
                    mCategory.setSelection(
                        maxOf(0, NoteCategory.indexOf(string(row, NotePad.Notes.COLUMN_NAME_CATEGORY)))
                    )
                } ?: run {
                    Toast.makeText(this, R.string.paste_source_missing, Toast.LENGTH_LONG).show()
                    return
                }
            } else {
                val text = item.coerceToText(this)
                if (text == null || text.isEmpty()) {
                    Toast.makeText(this, R.string.paste_empty, Toast.LENGTH_LONG).show()
                    return
                }
                mText.setText(text)
            }
        } catch (error: RuntimeException) {
            Log.e("NoteEditor", "Unable to paste note", error)
            Toast.makeText(this, R.string.paste_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun exportNote() {
        if (Build.VERSION.SDK_INT < 19) {
            Toast.makeText(this, R.string.export_requires_kitkat, Toast.LENGTH_LONG).show()
            return
        }
        if (!saveNote(true)) return
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TITLE, NoteExport.filename(mSavedTitle))
        try {
            startActivityForResult(intent, CREATE_TEXT_DOCUMENT)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.export_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != CREATE_TEXT_DOCUMENT || resultCode != RESULT_OK) return
        val targetUri = data?.data
        if (targetUri == null) {
            showExportResult(false)
            return
        }
        try {
            val noteUri = mUri ?: throw IllegalStateException("Note URI missing")
            val document = contentResolver.query(noteUri, null, null, null, null)?.useCompat { row ->
                if (!row.moveToFirst()) throw IllegalStateException("Note not found")
                val modified = row.getLong(row.getColumnIndexOrThrow(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE))
                val time = NoteTimestamp.format(modified, TimeZone.getDefault(), Locale.getDefault())
                NoteExport.document(
                    string(row, NotePad.Notes.COLUMN_NAME_TITLE),
                    string(row, NotePad.Notes.COLUMN_NAME_NOTE),
                    NoteCategory.label(this, string(row, NotePad.Notes.COLUMN_NAME_CATEGORY)),
                    time ?: getString(R.string.time_unknown)
                )
            } ?: throw IllegalStateException("Note not found")
            val task = NoteExportTask(applicationContext.contentResolver, document)
            mExportTask = task
            task.attach(this)
            task.execute(targetUri)
        } catch (error: RuntimeException) {
            Log.e("NoteEditor", "Unable to prepare export", error)
            showExportResult(false)
        }
    }

    internal fun showExportProgress() {
        setStatusBadge(
            R.string.exporting,
            R.drawable.status_badge_neutral,
            R.color.status_neutral_text
        )
    }

    internal fun showExportResult(success: Boolean) {
        updateStatus()
        Toast.makeText(
            this,
            if (success) R.string.export_success else R.string.export_failed,
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onRetainNonConfigurationInstance(): Any? {
        mExportTask?.detach()
        return mExportTask
    }

    override fun onDestroy() {
        mUnregisterBack?.run()
        mExportTask?.detach()
        super.onDestroy()
    }

    /** Keep API 33 platform types out of the editor's legacy API paths. */
    @TargetApi(33)
    private object BackNavigation {
        fun register(editor: NoteEditor): Runnable {
            val dispatcher = editor.onBackInvokedDispatcher
            val callback = OnBackInvokedCallback { editor.leaveEditor() }
            dispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                callback
            )
            return Runnable { dispatcher.unregisterOnBackInvokedCallback(callback) }
        }
    }

    companion object {
        private const val CREATE_TEXT_DOCUMENT = 701

        private fun stateString(state: Bundle, key: String, fallback: String): String {
            return state.getString(key) ?: fallback
        }

        private fun string(row: Cursor, column: String): String {
            return row.getString(row.getColumnIndexOrThrow(column)) ?: ""
        }

        /** Cursor only implemented java.io.Closeable starting at API 16; avoid kotlin.io.use on minSdk 11. */
        private inline fun <R> Cursor.useCompat(block: (Cursor) -> R): R {
            return try {
                block(this)
            } finally {
                close()
            }
        }
    }
}
