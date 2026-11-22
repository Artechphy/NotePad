package com.example.android.notepad;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.ContentProviderClient;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.LiveFolders;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.TextView;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.fakes.RoboMenuItem;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowActivity;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import static org.junit.Assert.*;

/**
 * Harness-driven parity tests covering subtle Java-to-Kotlin behavioral,
 * visibility, nullability, lifecycle, and reflection contracts across migration batches.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@LooperMode(LooperMode.Mode.LEGACY)
public class KotlinMigrationParityTest {
    private NotePadProvider provider;

    @After
    public void tearDown() {
        if (provider != null) {
            provider.getOpenHelperForTest().close();
            provider = null;
        }
        ContentProviderClient client = RuntimeEnvironment.getApplication().getContentResolver()
                .acquireContentProviderClient(NotePad.Notes.CONTENT_URI);
        if (client != null) {
            ((NotePadProvider) client.getLocalContentProvider()).getOpenHelperForTest().close();
            client.release();
        }
    }

    private void drainLoader() {
        for (int i = 0; i < 3; i++) {
            Robolectric.flushForegroundThreadScheduler();
            Robolectric.flushBackgroundThreadScheduler();
        }
        Robolectric.flushForegroundThreadScheduler();
    }

    private Uri insertNote(String title, String body, long modified) {
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, body);
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, modified);
        return RuntimeEnvironment.getApplication().getContentResolver()
                .insert(NotePad.Notes.CONTENT_URI, values);
    }

    @Test
    public void titleEditorOnCreateRemainsPublicAndXmlOnClickFinishesActivity() throws Exception {
        Method onCreate = TitleEditor.class.getDeclaredMethod("onCreate", Bundle.class);
        assertTrue("TitleEditor.onCreate must remain public after Kotlin override",
                Modifier.isPublic(onCreate.getModifiers()));

        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_TITLE, "原标题");
        Uri uri = RuntimeEnvironment.getApplication().getContentResolver()
                .insert(NotePad.Notes.CONTENT_URI, values);
        ActivityController<TitleEditor> controller = Robolectric.buildActivity(
                TitleEditor.class, new Intent(TitleEditor.EDIT_TITLE_ACTION, uri)).setup();
        try {
            TitleEditor editor = controller.get();
            editor.findViewById(R.id.ok).performClick();
            assertTrue("android:onClick=\"onClickOk\" must invoke TitleEditor.onClickOk",
                    editor.isFinishing());
        } finally {
            controller.pause().stop().destroy();
        }
    }

    @Test
    public void noteExportDocumentToleratesNullPartsLikeJavaStringConcat() {
        String rendered = NoteExport.document(null, null, null, null);
        assertEquals("null\n\n分类：null\n修改时间：null\n\nnull\n", rendered);
    }

    @Test
    public void trimMatchesJavaStringTrimSemanticsForControlCharsAndFullWidthSpaces() {
        assertEquals("", NoteSearch.normalize("\u0000\u0007\u001B"));
        assertEquals("正文", NoteText.title("\u0000\u0007", "正文"));
        assertEquals("　全角保留　", NoteSearch.normalize("　全角保留　"));
        assertEquals("　全角　", NoteText.preview("　全角　"));
    }

    @Test
    public void notePadProviderUpdateWithNullValuesPreservesNullPointerException() {
        provider = new NotePadProvider();
        ProviderInfo info = new ProviderInfo();
        info.authority = NotePad.AUTHORITY;
        provider.attachInfo(RuntimeEnvironment.getApplication(), info);
        Uri uri = provider.insert(NotePad.Notes.CONTENT_URI, new ContentValues());
        assertThrows(NullPointerException.class, () -> provider.update(uri, null, null, null));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void notesLiveFolderReturnsExpectedResultForSupportedAndUnsupportedActions() {
        ActivityController<NotesLiveFolder> supported = Robolectric.buildActivity(
                NotesLiveFolder.class, new Intent(LiveFolders.ACTION_CREATE_LIVE_FOLDER)).create();
        try {
            ShadowActivity shadow = Shadow.extract(supported.get());
            assertTrue(supported.get().isFinishing());
            assertEquals(Activity.RESULT_OK, shadow.getResultCode());
            assertNotNull(shadow.getResultIntent());
            assertEquals(NotePad.Notes.LIVE_FOLDER_URI, shadow.getResultIntent().getData());
        } finally {
            supported.destroy();
        }

        ActivityController<NotesLiveFolder> unsupported = Robolectric.buildActivity(
                NotesLiveFolder.class, new Intent(Intent.ACTION_VIEW)).create();
        try {
            ShadowActivity shadow = Shadow.extract(unsupported.get());
            assertTrue(unsupported.get().isFinishing());
            assertEquals(Activity.RESULT_CANCELED, shadow.getResultCode());
        } finally {
            unsupported.destroy();
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void notesListLoaderIsStaticNonInnerAndShowsErrorOnNullCursor() throws Exception {
        Class<?> loaderClass = Class.forName("com.example.android.notepad.NotesList$NotesLoader");
        assertTrue("NotesLoader must remain static (non-inner) to avoid leaking NotesList",
                Modifier.isStatic(loaderClass.getModifiers()));
        for (Field field : loaderClass.getDeclaredFields()) {
            assertNotEquals("NotesLoader must not hold synthetic outer NotesList reference",
                    NotesList.class, field.getType());
        }
        Constructor<?>[] constructors = loaderClass.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertEquals(Context.class, constructors[0].getParameterTypes()[0]);

        Robolectric.getBackgroundThreadScheduler().pause();
        Robolectric.getForegroundThreadScheduler().pause();
        ActivityController<NotesList> controller = Robolectric.buildActivity(NotesList.class).setup();
        try {
            drainLoader();
            NotesList activity = controller.get();
            activity.onLoadFinished(null, null);
            assertEquals(activity.getString(R.string.notes_load_error),
                    ((TextView) activity.findViewById(android.R.id.empty)).getText().toString());
            assertEquals(activity.getString(R.string.note_count, 0),
                    ((TextView) activity.findViewById(R.id.note_count)).getText().toString());
        } finally {
            controller.pause().stop().destroy();
            drainLoader();
        }
    }

    @Test
    public void notesListViewBinderMatchesJavaTrimAndEmptyPreviewSemantics() {
        Robolectric.getBackgroundThreadScheduler().pause();
        Robolectric.getForegroundThreadScheduler().pause();
        insertNote("\u0000\u0007", "", 1704139440000L);
        insertNote("　", "全角标题", 1704139441000L);
        ActivityController<NotesList> controller = Robolectric.buildActivity(NotesList.class).setup();
        try {
            drainLoader();
            NotesList activity = controller.get();
            assertEquals(2, activity.getListAdapter().getCount());

            View fullWidthRow = activity.getListAdapter().getView(0, null, activity.getListView());
            assertEquals("　", ((TextView) fullWidthRow.findViewById(android.R.id.text1)).getText().toString());

            View controlRow = activity.getListAdapter().getView(1, null, activity.getListView());
            assertEquals(activity.getString(R.string.untitled_note),
                    ((TextView) controlRow.findViewById(android.R.id.text1)).getText().toString());
            assertEquals(activity.getString(R.string.empty_preview),
                    ((TextView) controlRow.findViewById(R.id.note_preview)).getText().toString());
        } finally {
            controller.pause().stop().destroy();
            drainLoader();
        }
    }

    @Test
    public void notesListItemClickSupportsEditPickAndGetContent() {
        Robolectric.getBackgroundThreadScheduler().pause();
        Robolectric.getForegroundThreadScheduler().pause();
        Uri noteUri = insertNote("点击测试", "正文", 1704139440000L);
        long noteId = ContentUris.parseId(noteUri);

        ActivityController<NotesList> editController = Robolectric.buildActivity(NotesList.class).setup();
        try {
            drainLoader();
            NotesList activity = editController.get();
            activity.onListItemClick(activity.getListView(), null, 0, noteId);
            ShadowActivity shadow = Shadow.extract(activity);
            Intent started = shadow.getNextStartedActivity();
            assertNotNull(started);
            assertEquals(Intent.ACTION_EDIT, started.getAction());
            assertEquals(noteUri, started.getData());
            assertEquals(NoteEditor.class.getName(), started.getComponent().getClassName());
        } finally {
            editController.pause().stop().destroy();
            drainLoader();
        }

        ActivityController<NotesList> pickController = Robolectric.buildActivity(
                NotesList.class, new Intent(Intent.ACTION_PICK, NotePad.Notes.CONTENT_URI)).setup();
        try {
            drainLoader();
            NotesList activity = pickController.get();
            activity.onListItemClick(activity.getListView(), null, 0, noteId);
            ShadowActivity shadow = Shadow.extract(activity);
            assertEquals(Activity.RESULT_OK, shadow.getResultCode());
            assertNotNull(shadow.getResultIntent());
            assertEquals(noteUri, shadow.getResultIntent().getData());
            assertFalse("Original Java ACTION_PICK path does not auto-finish", activity.isFinishing());
        } finally {
            pickController.pause().stop().destroy();
            drainLoader();
        }
    }

    @Test
    public void notesListOptionsAndContextMenuHandleCopyDeleteAndBadMenuInfo() {
        Robolectric.getBackgroundThreadScheduler().pause();
        Robolectric.getForegroundThreadScheduler().pause();
        Uri noteUri = insertNote("上下文笔记", "内容", 1704139440000L);
        long noteId = ContentUris.parseId(noteUri);

        ActivityController<NotesList> controller = Robolectric.buildActivity(NotesList.class).setup();
        try {
            drainLoader();
            NotesList activity = controller.get();
            ShadowActivity shadow = Shadow.extract(activity);

            // Options menu add & paste
            assertTrue(activity.onOptionsItemSelected(new RoboMenuItem(R.id.menu_add)));
            Intent addIntent = shadow.getNextStartedActivity();
            assertEquals(Intent.ACTION_INSERT, addIntent.getAction());
            assertTrue(activity.onOptionsItemSelected(new RoboMenuItem(R.id.menu_paste)));
            Intent pasteIntent = shadow.getNextStartedActivity();
            assertEquals(Intent.ACTION_PASTE, pasteIntent.getAction());

            // Bad ContextMenuInfo must be caught gracefully without NPE even when view is null
            ContextMenu.ContextMenuInfo badInfo = new ContextMenu.ContextMenuInfo() {};
            activity.onCreateContextMenu(null, null, badInfo);
            MenuItem badItem = new MenuItemWithInfo(R.id.context_delete, badInfo);
            assertFalse(activity.onContextItemSelected(badItem));

            // Valid context menu open, copy, delete
            AdapterView.AdapterContextMenuInfo validInfo =
                    new AdapterView.AdapterContextMenuInfo(new View(activity), 0, noteId);
            assertTrue(activity.onContextItemSelected(new MenuItemWithInfo(R.id.context_open, validInfo)));
            Intent openIntent = shadow.getNextStartedActivity();
            assertEquals(Intent.ACTION_EDIT, openIntent.getAction());
            assertEquals(noteUri, openIntent.getData());

            assertTrue(activity.onContextItemSelected(new MenuItemWithInfo(R.id.context_copy, validInfo)));
            ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            assertNotNull(clipboard.getPrimaryClip());
            assertEquals(noteUri, clipboard.getPrimaryClip().getItemAt(0).getUri());

            assertTrue(activity.onContextItemSelected(new MenuItemWithInfo(R.id.context_delete, validInfo)));
            try (Cursor cursor = RuntimeEnvironment.getApplication().getContentResolver()
                    .query(NotePad.Notes.CONTENT_URI, null, null, null, null)) {
                assertNotNull(cursor);
                assertEquals(0, cursor.getCount());
            }
        } finally {
            controller.pause().stop().destroy();
            drainLoader();
        }
    }

    private static final class MenuItemWithInfo extends RoboMenuItem {
        private final ContextMenu.ContextMenuInfo menuInfo;

        MenuItemWithInfo(int itemId, ContextMenu.ContextMenuInfo menuInfo) {
            super(itemId);
            this.menuInfo = menuInfo;
        }

        @Override
        public ContextMenu.ContextMenuInfo getMenuInfo() {
            return menuInfo;
        }
    }
}
