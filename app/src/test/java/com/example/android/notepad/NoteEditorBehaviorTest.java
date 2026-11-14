package com.example.android.notepad;

import android.app.Activity;
import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.widget.EditText;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class NoteEditorBehaviorTest {
    private ActivityController<? extends Activity> controller;

    @After public void tearDown() {
        closeEditor();
        ContentProviderClient client = RuntimeEnvironment.getApplication().getContentResolver()
                .acquireContentProviderClient(NotePad.Notes.CONTENT_URI);
        if (client != null) {
            ((NotePadProvider) client.getLocalContentProvider()).getOpenHelperForTest().close();
            client.release();
        }
    }

    private void closeEditor() {
        if (controller != null) {
            controller.pause().stop().destroy();
            controller = null;
        }
    }

    private Uri seed() {
        ContentValues values = new ContentValues();
        values.put("title", "原标题");
        values.put("note", "原正文");
        values.put("created", 1000L);
        values.put("modified", 2000L);
        return RuntimeEnvironment.getApplication().getContentResolver()
                .insert(NotePad.Notes.CONTENT_URI, values);
    }

    private NoteEditor openNote(String action, Uri uri) {
        ActivityController<NoteEditor> editor = Robolectric.buildActivity(NoteEditor.class,
                new Intent(action, uri)).setup();
        controller = editor;
        return editor.get();
    }

    private Cursor query(Uri uri) {
        return RuntimeEnvironment.getApplication().getContentResolver()
                .query(uri, null, null, null, null);
    }

    @Test public void openingAndLeavingEditorDoesNotChangeModificationTime() {
        Uri uri = seed();
        openNote(Intent.ACTION_EDIT, uri);
        closeEditor();
        try (Cursor row = query(uri)) {
            assertTrue(row.moveToFirst());
            assertEquals(2000L, row.getLong(row.getColumnIndexOrThrow("modified")));
        }
    }

    @Test public void bodyEditSavesAndIsPresentWhenEditorReopens() {
        Uri uri = seed();
        NoteEditor editor = openNote(Intent.ACTION_EDIT, uri);
        ((EditText) editor.findViewById(R.id.note)).setText("修改后正文");
        closeEditor();
        try (Cursor row = query(uri)) {
            assertTrue(row.moveToFirst());
            assertEquals(1000L, row.getLong(row.getColumnIndexOrThrow("created")));
            assertTrue(row.getLong(row.getColumnIndexOrThrow("modified")) > 2000L);
        }
        editor = openNote(Intent.ACTION_EDIT, uri);
        assertEquals("修改后正文", ((EditText) editor.findViewById(R.id.note)).getText().toString());
    }

    @Test public void newNoteIsSavedAndAnAbandonedEmptyNoteIsDeleted() {
        NoteEditor editor = openNote(Intent.ACTION_INSERT, NotePad.Notes.CONTENT_URI);
        ((EditText) editor.findViewById(R.id.note)).setText("期中实验笔记");
        editor.finish();
        closeEditor();
        editor = openNote(Intent.ACTION_INSERT, NotePad.Notes.CONTENT_URI);
        editor.finish();
        closeEditor();
        try (Cursor rows = query(NotePad.Notes.CONTENT_URI)) {
            assertEquals(1, rows.getCount());
            assertTrue(rows.moveToFirst());
            assertEquals("期中实验笔记", rows.getString(rows.getColumnIndexOrThrow("note")));
        }
    }

    @Test public void titleEditorAlsoUpdatesModificationTime() {
        Uri uri = seed();
        ActivityController<TitleEditor> title = Robolectric.buildActivity(TitleEditor.class,
                new Intent(TitleEditor.EDIT_TITLE_ACTION, uri)).setup();
        controller = title;
        ((EditText) title.get().findViewById(R.id.title)).setText("修改后标题");
        closeEditor();
        try (Cursor row = query(uri)) {
            assertTrue(row.moveToFirst());
            assertEquals("修改后标题", row.getString(row.getColumnIndexOrThrow("title")));
            assertTrue(row.getLong(row.getColumnIndexOrThrow("modified")) > 2000L);
            assertEquals(1000L, row.getLong(row.getColumnIndexOrThrow("created")));
        }
    }
    private android.view.MenuItem menuItem(NoteEditor editor, int id) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(editor, editor.findViewById(R.id.editor_more));
        editor.onCreateOptionsMenu(popup.getMenu());
        return popup.getMenu().findItem(id);
    }

    @Test public void titleOnlyNoteAndCategorySurviveSaveAndReopening() {
        NoteEditor editor = openNote(Intent.ACTION_INSERT, NotePad.Notes.CONTENT_URI);
        ((EditText) editor.findViewById(R.id.title)).setText("只有标题的笔记");
        ((android.widget.Spinner) editor.findViewById(R.id.editor_category)).setSelection(1);
        editor.findViewById(R.id.editor_save).performClick();
        assertTrue(editor.isFinishing());
        closeEditor();
        Uri uri;
        try (Cursor row = query(NotePad.Notes.CONTENT_URI)) {
            assertEquals(1, row.getCount());
            assertTrue(row.moveToFirst());
            assertEquals("只有标题的笔记", row.getString(row.getColumnIndexOrThrow("title")));
            assertEquals("study", row.getString(row.getColumnIndexOrThrow("category")));
            uri = android.content.ContentUris.withAppendedId(NotePad.Notes.CONTENT_URI,
                    row.getLong(row.getColumnIndexOrThrow("_id")));
        }
        editor = openNote(Intent.ACTION_EDIT, uri);
        assertEquals("只有标题的笔记", ((EditText) editor.findViewById(R.id.title)).getText().toString());
        assertEquals(1, ((android.widget.Spinner) editor.findViewById(R.id.editor_category)).getSelectedItemPosition());
    }

    @Test public void recreatingNewEditorReusesDraftAndPreservesFields() {
        NoteEditor editor = openNote(Intent.ACTION_INSERT, NotePad.Notes.CONTENT_URI);
        ((EditText) editor.findViewById(R.id.title)).setText("新草稿");
        ((EditText) editor.findViewById(R.id.note)).setText("旋转后仍在");
        ((android.widget.Spinner) editor.findViewById(R.id.editor_category)).setSelection(2);
        controller.recreate();
        editor = (NoteEditor) controller.get();
        assertEquals("新草稿", ((EditText) editor.findViewById(R.id.title)).getText().toString());
        assertEquals("旋转后仍在", ((EditText) editor.findViewById(R.id.note)).getText().toString());
        assertEquals(2, ((android.widget.Spinner) editor.findViewById(R.id.editor_category)).getSelectedItemPosition());
        try (Cursor rows = query(NotePad.Notes.CONTENT_URI)) { assertEquals(1, rows.getCount()); }
    }

    @Test public void revertingRestoresTitleBodyAndCategoryTogether() {
        Uri uri = seed();
        NoteEditor editor = openNote(Intent.ACTION_EDIT, uri);
        ((EditText) editor.findViewById(R.id.title)).setText("改动标题");
        ((EditText) editor.findViewById(R.id.note)).setText("改动正文");
        ((android.widget.Spinner) editor.findViewById(R.id.editor_category)).setSelection(3);
        editor.onOptionsItemSelected(menuItem(editor, R.id.menu_revert));
        closeEditor();
        try (Cursor row = query(uri)) {
            assertTrue(row.moveToFirst());
            assertEquals("原标题", row.getString(row.getColumnIndexOrThrow("title")));
            assertEquals("原正文", row.getString(row.getColumnIndexOrThrow("note")));
            assertEquals("uncategorized", row.getString(row.getColumnIndexOrThrow("category")));
        }
    }

    @Test public void exportSavesDraftAndLaunchesSystemDocumentIntent() {
        NoteEditor editor = openNote(Intent.ACTION_INSERT, NotePad.Notes.CONTENT_URI);
        ((EditText) editor.findViewById(R.id.title)).setText("课程/笔记");
        ((EditText) editor.findViewById(R.id.note)).setText("正文");
        editor.onOptionsItemSelected(menuItem(editor, R.id.menu_export));
        org.robolectric.shadows.ShadowActivity.IntentForResult started =
                ((org.robolectric.shadows.ShadowActivity) org.robolectric.shadow.api.Shadow.extract(editor)).getNextStartedActivityForResult();
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, started.intent.getAction());
        assertEquals("text/plain", started.intent.getType());
        assertEquals("课程_笔记.txt", started.intent.getStringExtra(Intent.EXTRA_TITLE));
        assertTrue(started.intent.hasCategory(Intent.CATEGORY_OPENABLE));
        try (Cursor row = query(NotePad.Notes.CONTENT_URI)) {
            assertTrue(row.moveToFirst());
            assertEquals("正文", row.getString(row.getColumnIndexOrThrow("note")));
        }
        editor.onActivityResult(started.requestCode, Activity.RESULT_CANCELED, null);
        assertNull(org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
    }

    @Test
    @org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.LEGACY)
    public void exportWritesChosenStreamAndReportsFailureWithoutChangingSourceTime() throws Exception {
        Robolectric.getBackgroundThreadScheduler().pause();
        Robolectric.getForegroundThreadScheduler().pause();
        Uri uri = seed();
        NoteEditor editor = openNote(Intent.ACTION_EDIT, uri);
        Uri target = Uri.parse("content://test-output/note.txt");
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        ((org.robolectric.shadows.ShadowContentResolver) org.robolectric.shadow.api.Shadow.extract(RuntimeEnvironment.getApplication().getContentResolver()))
                .registerOutputStream(target, bytes);
        editor.onActivityResult(701, Activity.RESULT_OK, new Intent().setData(target));
        Robolectric.flushBackgroundThreadScheduler();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertTrue(bytes.toString("UTF-8").contains("原标题"));
        assertTrue(bytes.toString("UTF-8").contains("原正文"));
        assertEquals("笔记已导出", org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
        try (Cursor row = query(uri)) {
            assertTrue(row.moveToFirst());
            assertEquals(2000L, row.getLong(row.getColumnIndexOrThrow("modified")));
        }
        ((org.robolectric.shadows.ShadowContentResolver) org.robolectric.shadow.api.Shadow.extract(RuntimeEnvironment.getApplication().getContentResolver()))
                .registerOutputStream(target, new java.io.OutputStream() {
                    @Override public void write(int value) throws java.io.IOException { throw new java.io.IOException("full"); }
                });
        editor.onActivityResult(701, Activity.RESULT_OK, new Intent().setData(target));
        Robolectric.flushBackgroundThreadScheduler();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertEquals("导出失败，目标文件可能不完整，请重试", org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
    }

}
