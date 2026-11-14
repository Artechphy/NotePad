package com.example.android.notepad;

import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class NoteEditorSaveFailureTest {
    private ActivityController<NoteEditor> controller;
    private Uri source;

    @After public void tearDown() {
        if (controller != null) controller.pause().stop().destroy();
        ContentProviderClient client = RuntimeEnvironment.getApplication().getContentResolver()
                .acquireContentProviderClient(NotePad.Notes.CONTENT_URI);
        if (client != null) {
            ((NotePadProvider) client.getLocalContentProvider()).getOpenHelperForTest().close();
            client.release();
        }
    }

    private NoteEditor editRemovedSource() {
        ContentValues values = new ContentValues();
        values.put("title", "原笔记");
        values.put("note", "原正文");
        source = RuntimeEnvironment.getApplication().getContentResolver()
                .insert(NotePad.Notes.CONTENT_URI, values);
        controller = Robolectric.buildActivity(NoteEditor.class,
                new Intent(Intent.ACTION_EDIT, source)).setup();
        NoteEditor editor = controller.get();
        ((EditText) editor.findViewById(R.id.title)).setText("未保存标题");
        ((EditText) editor.findViewById(R.id.note)).setText("未保存正文\n第二行📘");
        ((Spinner) editor.findViewById(R.id.editor_category)).setSelection(2);
        RuntimeEnvironment.getApplication().getContentResolver().delete(source, null, null);
        return editor;
    }

    private void assertInput(NoteEditor editor) {
        assertEquals("未保存标题", ((EditText) editor.findViewById(R.id.title)).getText().toString());
        assertEquals("未保存正文\n第二行📘", ((EditText) editor.findViewById(R.id.note)).getText().toString());
        assertEquals(2, ((Spinner) editor.findViewById(R.id.editor_category)).getSelectedItemPosition());
    }

    @Test public void toolbarBackKeepsFailedInputVisible() {
        NoteEditor editor = editRemovedSource();
        editor.findViewById(R.id.editor_back).performClick();
        assertFalse("Failed save must not close the editor", editor.isFinishing());
        assertInput(editor);
        assertEquals("保存失败，请重试", ((TextView) editor.findViewById(R.id.save_status)).getText());
    }

    @Test public void systemBackKeepsFailedInputVisible() {
        NoteEditor editor = editRemovedSource();
        editor.onBackPressed();
        assertFalse("System Back must also guard failed saves", editor.isFinishing());
        assertInput(editor);
    }

    @Test public void backgroundFailureIsVisibleAndInputRemains() {
        NoteEditor editor = editRemovedSource();
        controller.pause().resume();
        assertInput(editor);
        assertEquals("自动保存失败，内容仍保留，请返回重试", ShadowToast.getTextOfLatestToast());
        assertEquals("保存失败，请重试", ((TextView) editor.findViewById(R.id.save_status)).getText());
    }

    @Test public void missingSourceCanRecreateWithoutDiscardingInput() {
        editRemovedSource();
        controller.recreate();
        NoteEditor editor = controller.get();
        assertFalse("A recoverable editor must open even when the source is missing", editor.isFinishing());
        assertInput(editor);
        assertEquals("保存失败，请重试", ((TextView) editor.findViewById(R.id.save_status)).getText());
        try (Cursor rows = RuntimeEnvironment.getApplication().getContentResolver()
                .query(NotePad.Notes.CONTENT_URI, null, null, null, null)) {
            assertEquals("Recovery must not silently recreate a deleted source", 0, rows.getCount());
        }
    }

    @Test public void explicitSaveAsNewRecoversFieldsAndPreservesOtherNotes() {
        ContentValues other = new ContentValues();
        other.put("title", "保留笔记");
        other.put("note", "不应被覆盖");
        Uri otherUri = RuntimeEnvironment.getApplication().getContentResolver()
                .insert(NotePad.Notes.CONTENT_URI, other);
        NoteEditor editor = editRemovedSource();
        editor.findViewById(R.id.editor_back).performClick();
        android.widget.PopupMenu popup = new android.widget.PopupMenu(editor, editor.findViewById(R.id.editor_more));
        editor.onCreateOptionsMenu(popup.getMenu());
        editor.onPrepareOptionsMenu(popup.getMenu());
        assertTrue(popup.getMenu().findItem(R.id.menu_save_as_new).isVisible());
        editor.onOptionsItemSelected(popup.getMenu().findItem(R.id.menu_save_as_new));
        assertTrue(editor.isFinishing());
        controller.pause().stop().destroy(); controller = null;
        try (Cursor rows = RuntimeEnvironment.getApplication().getContentResolver()
                .query(NotePad.Notes.CONTENT_URI, null, null, null, null)) {
            assertEquals(2, rows.getCount());
            boolean recovered = false;
            while (rows.moveToNext()) {
                if ("未保存标题".equals(rows.getString(rows.getColumnIndexOrThrow("title")))) {
                    recovered = true;
                    assertEquals("未保存正文\n第二行📘", rows.getString(rows.getColumnIndexOrThrow("note")));
                    assertEquals("life", rows.getString(rows.getColumnIndexOrThrow("category")));
                }
            }
            assertTrue(recovered);
        }
        try (Cursor row = RuntimeEnvironment.getApplication().getContentResolver()
                .query(otherUri, null, null, null, null)) {
            assertTrue(row.moveToFirst());
            assertEquals("不应被覆盖", row.getString(row.getColumnIndexOrThrow("note")));
        }
    }

    @Test public void returnCanRetryAfterSourceIsRestored() {
        NoteEditor editor = editRemovedSource();
        editor.findViewById(R.id.editor_back).performClick();
        assertFalse(editor.isFinishing());
        ContentValues original = new ContentValues();
        original.put("_id", Long.parseLong(source.getLastPathSegment()));
        original.put("title", "原笔记");
        original.put("note", "原正文");
        RuntimeEnvironment.getApplication().getContentResolver().insert(NotePad.Notes.CONTENT_URI, original);
        editor.findViewById(R.id.editor_back).performClick();
        assertTrue(editor.isFinishing());
        try (Cursor row = RuntimeEnvironment.getApplication().getContentResolver()
                .query(source, null, null, null, null)) {
            assertTrue(row.moveToFirst());
            assertEquals("未保存标题", row.getString(row.getColumnIndexOrThrow("title")));
            assertEquals("未保存正文\n第二行📘", row.getString(row.getColumnIndexOrThrow("note")));
            assertEquals("life", row.getString(row.getColumnIndexOrThrow("category")));
        }
    }

    @Test public void returningFromEmptyNewDraftStillRemovesIt() {
        controller = Robolectric.buildActivity(NoteEditor.class,
                new Intent(Intent.ACTION_INSERT, NotePad.Notes.CONTENT_URI)).setup();
        NoteEditor editor = controller.get();
        editor.findViewById(R.id.editor_back).performClick();
        assertTrue(editor.isFinishing());
        try (Cursor rows = RuntimeEnvironment.getApplication().getContentResolver()
                .query(NotePad.Notes.CONTENT_URI, null, null, null, null)) {
            assertEquals(0, rows.getCount());
        }
    }
}
