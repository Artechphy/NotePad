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

package com.example.android.notepad;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;
import java.util.TimeZone;

/** One editor for title, body and category. Leaving it saves actual changes. */
public class NoteEditor extends Activity {
    private static final int CREATE_TEXT_DOCUMENT = 701;
    private Uri mUri;
    private EditText mTitle;
    private EditText mText;
    private Spinner mCategory;
    private TextView mStatus;
    private boolean mInitialized;
    private boolean mBinding;
    private boolean mNewNote;
    private boolean mDeleted;
    private boolean mPastePending;
    private String mOriginalTitle;
    private String mOriginalBody;
    private String mOriginalCategory;
    private String mSavedTitle;
    private String mSavedBody;
    private String mSavedCategory;
    private NoteExportTask mExportTask;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.note_editor);
        mTitle = (EditText) findViewById(R.id.title);
        mText = (EditText) findViewById(R.id.note);
        mCategory = (Spinner) findViewById(R.id.editor_category);
        mStatus = (TextView) findViewById(R.id.save_status);
        ArrayAdapter<String> categories = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, NoteCategory.labels(this));
        categories.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mCategory.setAdapter(categories);

        try {
            if (state != null && state.getString("note_uri") != null) {
                mUri = Uri.parse(state.getString("note_uri"));
                mNewNote = state.getBoolean("new_note");
            } else {
                String action = getIntent().getAction();
                if (Intent.ACTION_EDIT.equals(action) || Intent.ACTION_VIEW.equals(action)) {
                    mUri = getIntent().getData();
                } else if (Intent.ACTION_INSERT.equals(action) || Intent.ACTION_PASTE.equals(action)) {
                    ContentValues values = new ContentValues();
                    values.put(NotePad.Notes.COLUMN_NAME_TITLE, "");
                    String category = getIntent().getStringExtra(NotePad.Notes.COLUMN_NAME_CATEGORY);
                    values.put(NotePad.Notes.COLUMN_NAME_CATEGORY,
                            NoteCategory.indexOf(category) >= 0 ? category : NoteCategory.DEFAULT);
                    mUri = getContentResolver().insert(getIntent().getData(), values);
                    mNewNote = true;
                } else {
                    throw new IllegalArgumentException("Unsupported editor action");
                }
            }
            if (mUri == null) throw new IllegalArgumentException("No note URI");
            try (Cursor row = getContentResolver().query(mUri, null, null, null, null)) {
                if (row == null || !row.moveToFirst()) throw new IllegalArgumentException("Note not found");
                mSavedTitle = string(row, NotePad.Notes.COLUMN_NAME_TITLE);
                mSavedBody = string(row, NotePad.Notes.COLUMN_NAME_NOTE);
                mSavedCategory = string(row, NotePad.Notes.COLUMN_NAME_CATEGORY);
            }
            mOriginalTitle = state == null ? mSavedTitle : stateString(state, "original_title", mSavedTitle);
            mOriginalBody = state == null ? mSavedBody : stateString(state, "original_body", mSavedBody);
            mOriginalCategory = state == null ? mSavedCategory : stateString(state, "original_category", mSavedCategory);
            mBinding = true;
            mTitle.setText(mSavedTitle);
            mText.setText(mSavedBody);
            mCategory.setSelection(Math.max(0, NoteCategory.indexOf(mSavedCategory)));
            mBinding = false;
            mInitialized = true;
            setResult(RESULT_OK, new Intent().setData(mUri));
        } catch (RuntimeException error) {
            Log.e("NoteEditor", "Unable to open note", error);
            Toast.makeText(this, R.string.error_message, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateStatus(); }
            @Override public void afterTextChanged(Editable s) {}
        };
        mTitle.addTextChangedListener(watcher);
        mText.addTextChangedListener(watcher);
        mCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { updateStatus(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        findViewById(R.id.editor_back).setOnClickListener(view -> finish());
        findViewById(R.id.editor_save).setOnClickListener(view -> saveAndFinish());
        findViewById(R.id.editor_more).setOnClickListener(this::showActions);
        updateStatus();
        Object retained = getLastNonConfigurationInstance();
        if (retained instanceof NoteExportTask) {
            mExportTask = (NoteExportTask) retained;
            mExportTask.attach(this);
        }
        mPastePending = state == null ? Intent.ACTION_PASTE.equals(getIntent().getAction())
                : state.getBoolean("paste_pending");
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Android restricts clipboard reads to the focused app. onCreate/onResume are too
        // early during an Activity transition. Consume once so later focus cannot overwrite edits.
        if (hasFocus && mInitialized && mPastePending && !mDeleted && !isFinishing()) {
            mPastePending = false;
            pasteNote();
        }
    }

    private static String stateString(Bundle state, String key, String fallback) {
        String value = state.getString(key);
        return value == null ? fallback : value;
    }

    private static String string(Cursor row, String column) {
        String value = row.getString(row.getColumnIndexOrThrow(column));
        return value == null ? "" : value;
    }

    private String category() {
        return NoteCategory.idAt(Math.max(0, mCategory.getSelectedItemPosition()));
    }

    private boolean isEmpty() {
        return mTitle.getText().toString().trim().isEmpty() && mText.getText().toString().trim().isEmpty();
    }

    private void updateStatus() {
        if (!mInitialized || mBinding) return;
        boolean changed = !mTitle.getText().toString().equals(mSavedTitle)
                || !mText.getText().toString().equals(mSavedBody) || !category().equals(mSavedCategory);
        mStatus.setText(changed ? R.string.editing : mNewNote && isEmpty() ? R.string.new_draft : R.string.saved);
    }

    private boolean saveNote(boolean notify) {
        if (!mInitialized || mDeleted) return false;
        if (mNewNote && isEmpty()) {
            if (notify) Toast.makeText(this, R.string.nothing_to_save, Toast.LENGTH_SHORT).show();
            return false;
        }
        String body = mText.getText().toString();
        String title = NoteText.title(mTitle.getText().toString(), body);
        if (title.isEmpty()) title = getString(R.string.untitled_note);
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, body);
        values.put(NotePad.Notes.COLUMN_NAME_CATEGORY, category());
        try {
            // A zero update count also means an unchanged note. Verify existence separately.
            try (Cursor row = getContentResolver().query(mUri,
                    new String[] { NotePad.Notes._ID }, null, null, null)) {
                if (row == null || !row.moveToFirst()) throw new IllegalStateException("Note was removed");
            }
            getContentResolver().update(mUri, values, null, null);
            mSavedTitle = title;
            mSavedBody = body;
            mSavedCategory = category();
            mBinding = true;
            if (!mTitle.getText().toString().equals(title)) mTitle.setText(title);
            mBinding = false;
            mStatus.setText(R.string.saved);
            return true;
        } catch (RuntimeException error) {
            Log.e("NoteEditor", "Unable to save note", error);
            mStatus.setText(R.string.save_failed);
            if (notify) Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void saveAndFinish() {
        if (saveNote(true)) finish();
    }

    @Override protected void onPause() {
        super.onPause();
        if (!mInitialized || mDeleted) return;
        if (isFinishing() && mNewNote && isEmpty()) removeNote();
        else saveNote(false);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (mInitialized && !mDeleted) {
            saveNote(false);
            state.putString("note_uri", mUri.toString());
            state.putBoolean("new_note", mNewNote);
            state.putBoolean("paste_pending", mPastePending);
            state.putString("original_title", mOriginalTitle);
            state.putString("original_body", mOriginalBody);
            state.putString("original_category", mOriginalCategory);
        }
        super.onSaveInstanceState(state);
    }

    private void showActions(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        onCreateOptionsMenu(popup.getMenu());
        onPrepareOptionsMenu(popup.getMenu());
        popup.setOnMenuItemClickListener(this::onOptionsItemSelected);
        popup.show();
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.editor_options_menu, menu);
        return true;
    }

    @Override public boolean onPrepareOptionsMenu(Menu menu) {
        if (!mInitialized) return false;
        boolean changed = !mTitle.getText().toString().equals(mOriginalTitle)
                || !mText.getText().toString().equals(mOriginalBody) || !category().equals(mOriginalCategory);
        menu.findItem(R.id.menu_revert).setVisible(changed);
        menu.findItem(R.id.menu_export).setEnabled(Build.VERSION.SDK_INT >= 19);
        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_save) saveAndFinish();
        else if (id == R.id.menu_export) exportNote();
        else if (id == R.id.menu_delete) {
            new AlertDialog.Builder(this).setMessage(R.string.delete_confirm)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.menu_delete, (dialog, which) -> {
                        if (removeNote()) finish();
                    }).show();
        } else if (id == R.id.menu_revert) revertNote();
        else return super.onOptionsItemSelected(item);
        return true;
    }

    private boolean removeNote() {
        try {
            getContentResolver().delete(mUri, null, null);
            mDeleted = true;
            setResult(RESULT_CANCELED);
            return true;
        } catch (RuntimeException error) {
            Log.e("NoteEditor", "Unable to delete note", error);
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void revertNote() {
        if (mNewNote) {
            if (removeNote()) finish();
        } else {
            mBinding = true;
            mTitle.setText(mOriginalTitle);
            mText.setText(mOriginalBody);
            mCategory.setSelection(Math.max(0, NoteCategory.indexOf(mOriginalCategory)));
            mBinding = false;
            if (saveNote(true)) finish();
        }
    }

    private void pasteNote() {
        try {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                    getSystemService(CLIPBOARD_SERVICE);
            android.content.ClipData clip = clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                Toast.makeText(this, R.string.paste_empty, Toast.LENGTH_LONG).show();
                return;
            }
            android.content.ClipData.Item item = clip.getItemAt(0);
            if (item.getUri() != null && NotePad.AUTHORITY.equals(item.getUri().getAuthority())) {
                // SQLite can reuse the last deleted row ID for this new draft. A stale
                // clipboard URI must not be mistaken for a readable source note in that case.
                if (item.getUri().equals(mUri)) {
                    Toast.makeText(this, R.string.paste_source_missing, Toast.LENGTH_LONG).show();
                    return;
                }
                try (Cursor row = getContentResolver().query(item.getUri(), null, null, null, null)) {
                    if (row == null || !row.moveToFirst()) {
                        Toast.makeText(this, R.string.paste_source_missing, Toast.LENGTH_LONG).show();
                        return;
                    }
                    mTitle.setText(string(row, NotePad.Notes.COLUMN_NAME_TITLE));
                    mText.setText(string(row, NotePad.Notes.COLUMN_NAME_NOTE));
                    mCategory.setSelection(Math.max(0, NoteCategory.indexOf(string(row, NotePad.Notes.COLUMN_NAME_CATEGORY))));
                }
            } else {
                CharSequence text = item.coerceToText(this);
                if (text == null || text.length() == 0) {
                    Toast.makeText(this, R.string.paste_empty, Toast.LENGTH_LONG).show();
                    return;
                }
                mText.setText(text);
            }
        } catch (RuntimeException error) {
            Log.e("NoteEditor", "Unable to paste note", error);
            Toast.makeText(this, R.string.paste_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void exportNote() {
        if (Build.VERSION.SDK_INT < 19) {
            Toast.makeText(this, R.string.export_requires_kitkat, Toast.LENGTH_LONG).show();
            return;
        }
        if (!saveNote(true)) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/plain").putExtra(Intent.EXTRA_TITLE, NoteExport.filename(mSavedTitle));
        try {
            startActivityForResult(intent, CREATE_TEXT_DOCUMENT);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, R.string.export_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != CREATE_TEXT_DOCUMENT || resultCode != RESULT_OK) return;
        if (data == null || data.getData() == null) {
            showExportResult(false);
            return;
        }
        try (Cursor row = getContentResolver().query(mUri, null, null, null, null)) {
            if (row == null || !row.moveToFirst()) throw new IllegalStateException("Note not found");
            long modified = row.getLong(row.getColumnIndexOrThrow(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE));
            String time = NoteTimestamp.format(modified, TimeZone.getDefault(), Locale.getDefault());
            String document = NoteExport.document(string(row, NotePad.Notes.COLUMN_NAME_TITLE),
                    string(row, NotePad.Notes.COLUMN_NAME_NOTE),
                    NoteCategory.label(this, string(row, NotePad.Notes.COLUMN_NAME_CATEGORY)),
                    time == null ? getString(R.string.time_unknown) : time);
            mExportTask = new NoteExportTask(getApplicationContext().getContentResolver(), document);
            mExportTask.attach(this);
            mExportTask.execute(data.getData());
        } catch (RuntimeException error) {
            Log.e("NoteEditor", "Unable to prepare export", error);
            showExportResult(false);
        }
    }

    void showExportProgress() {
        mStatus.setText(R.string.exporting);
    }

    void showExportResult(boolean success) {
        updateStatus();
        Toast.makeText(this, success ? R.string.export_success : R.string.export_failed, Toast.LENGTH_LONG).show();
    }

    @Override public Object onRetainNonConfigurationInstance() {
        if (mExportTask != null) mExportTask.detach();
        return mExportTask;
    }

    @Override protected void onDestroy() {
        if (mExportTask != null) mExportTask.detach();
        super.onDestroy();
    }
}
