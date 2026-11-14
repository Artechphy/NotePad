package com.example.android.notepad;

import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Spinner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowClipboardManager;
import org.robolectric.shadows.ShadowToast;
import static org.junit.Assert.*;

/** Model the platform's external focus restriction; the editor and provider stay real. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, shadows = NotePasteLifecycleTest.FocusedClipboard.class)
public class NotePasteLifecycleTest {
    @Implements(ClipboardManager.class)
    public static class FocusedClipboard extends ShadowClipboardManager {
        static boolean focused;
        static int deniedReads;

        @Implementation @Override protected boolean hasPrimaryClip() {
            if (!focused) { deniedReads++; return false; }
            return super.hasPrimaryClip();
        }

        @Implementation @Override protected ClipData getPrimaryClip() {
            if (!focused) { deniedReads++; return null; }
            return super.getPrimaryClip();
        }
    }

    private ActivityController<NoteEditor> controller;
    private ClipboardManager clipboard;

    @Before public void setUp() {
        FocusedClipboard.focused = false;
        FocusedClipboard.deniedReads = 0;
        clipboard = (ClipboardManager) RuntimeEnvironment.getApplication()
                .getSystemService(android.content.Context.CLIPBOARD_SERVICE);
    }

    @After public void tearDown() {
        if (controller != null) controller.pause().stop().destroy();
        ContentProviderClient client = RuntimeEnvironment.getApplication().getContentResolver()
                .acquireContentProviderClient(NotePad.Notes.CONTENT_URI);
        if (client != null) {
            ((NotePadProvider) client.getLocalContentProvider()).getOpenHelperForTest().close();
            client.release();
        }
    }

    private Uri seed() {
        ContentValues values = new ContentValues();
        values.put("title", "周会纪要");
        values.put("note", "讨论版本发布。");
        values.put("category", "work");
        return RuntimeEnvironment.getApplication().getContentResolver()
                .insert(NotePad.Notes.CONTENT_URI, values);
    }

    private NoteEditor openPaste(Bundle state) {
        controller = Robolectric.buildActivity(NoteEditor.class,
                new Intent(Intent.ACTION_PASTE, NotePad.Notes.CONTENT_URI));
        controller.create(state).start();
        if (state != null) controller.restoreInstanceState(state);
        controller.resume(); // Deliberately no window focus yet.
        return controller.get();
    }

    private void focus() {
        FocusedClipboard.focused = true;
        controller.get().onWindowFocusChanged(true);
    }

    private String text(int id) {
        return ((EditText) controller.get().findViewById(id)).getText().toString();
    }

    private int count() {
        try (Cursor rows = RuntimeEnvironment.getApplication().getContentResolver()
                .query(NotePad.Notes.CONTENT_URI, null, null, null, null)) {
            return rows.getCount();
        }
    }

    @Test public void notePasteWaitsForFocusAndCopiesAllThreeFields() {
        Uri original = seed();
        clipboard.setPrimaryClip(ClipData.newUri(RuntimeEnvironment.getApplication()
                .getContentResolver(), "note", original));
        openPaste(null);
        assertEquals("No clipboard read is allowed before focus", 0, FocusedClipboard.deniedReads);
        assertEquals("", text(R.id.title));
        focus();
        assertEquals("周会纪要", text(R.id.title));
        assertEquals("讨论版本发布。", text(R.id.note));
        assertEquals(3, ((Spinner) controller.get().findViewById(R.id.editor_category)).getSelectedItemPosition());
        assertEquals(2, count());
    }

    @Test public void focusChangesAndRecreationNeverOverwriteUserEdits() {
        seed();
        clipboard.setPrimaryClip(ClipData.newPlainText("text", "最初粘贴"));
        openPaste(null); focus();
        ((EditText) controller.get().findViewById(R.id.note)).setText("我自己的补充");
        clipboard.setPrimaryClip(ClipData.newPlainText("later", "后来复制的内容"));
        controller.get().onWindowFocusChanged(false); focus();
        assertEquals("我自己的补充", text(R.id.note));
        controller.recreate(); focus();
        assertEquals("我自己的补充", text(R.id.note));
        assertEquals(2, count());
    }

    @Test public void pendingPasteSurvivesRecreationBeforeFirstFocus() {
        seed();
        clipboard.setPrimaryClip(ClipData.newPlainText("text", "重建后粘贴"));
        openPaste(null);
        Bundle state = new Bundle();
        controller.saveInstanceState(state).pause().stop().destroy();
        controller = null;
        openPaste(state);
        assertEquals(0, FocusedClipboard.deniedReads);
        focus();
        assertEquals("重建后粘贴", text(R.id.note));
        assertEquals(2, count());
    }

    @Test public void plainTextPastePreservesUnicodeAndNewlines() {
        seed();
        clipboard.setPrimaryClip(ClipData.newPlainText("text", "外部文本\n第二行 📘"));
        openPaste(null); focus();
        assertEquals("外部文本\n第二行 📘", text(R.id.note));
        assertEquals("", text(R.id.title));
    }

    @Test public void emptyClipboardReportsItAndLeavingRemovesOnlyEmptyDraft() {
        seed();
        openPaste(null); focus();
        assertEquals("剪贴板为空或暂不可读取", ShadowToast.getTextOfLatestToast());
        controller.get().finish();
        controller.pause().stop().destroy(); controller = null;
        assertEquals(1, count());
    }

    @Test public void deletedSourceReportsItWithoutCreatingASavedCopy() {
        Uri original = seed();
        clipboard.setPrimaryClip(ClipData.newUri(RuntimeEnvironment.getApplication().getContentResolver(), "note", original));
        RuntimeEnvironment.getApplication().getContentResolver().delete(original, null, null);
        openPaste(null); focus();
        assertEquals("原笔记已删除或无法读取", ShadowToast.getTextOfLatestToast());
        assertEquals("", text(R.id.note));
        controller.get().finish();
        controller.pause().stop().destroy(); controller = null;
        assertEquals(0, count());
    }

    @Test public void listMenuDoesNotReadClipboardWhileWindowIsUnfocused() {
        ActivityController<NotesList> list = Robolectric.buildActivity(NotesList.class).setup().visible();
        try {
            list.windowFocusChanged(false);
            FocusedClipboard.deniedReads = 0;
            android.view.Menu menu = new android.widget.PopupMenu(list.get(),
                    list.get().findViewById(R.id.list_more)).getMenu();
            list.get().onCreateOptionsMenu(menu);
            list.get().onPrepareOptionsMenu(menu);
            assertEquals(0, FocusedClipboard.deniedReads);
            assertFalse(menu.findItem(R.id.menu_paste).isEnabled());

            list.windowFocusChanged(true);
            FocusedClipboard.focused = true;
            list.get().onPrepareOptionsMenu(menu);
            assertFalse(menu.findItem(R.id.menu_paste).isEnabled());
            clipboard.setPrimaryClip(ClipData.newPlainText("text", "可粘贴内容"));
            list.get().onPrepareOptionsMenu(menu);
            assertTrue(menu.findItem(R.id.menu_paste).isEnabled());

            list.windowFocusChanged(false);
            FocusedClipboard.focused = false;
            list.get().onPrepareOptionsMenu(menu);
            assertEquals(0, FocusedClipboard.deniedReads);
            assertFalse(menu.findItem(R.id.menu_paste).isEnabled());
        } finally {
            list.pause().stop().destroy();
        }
    }
}
