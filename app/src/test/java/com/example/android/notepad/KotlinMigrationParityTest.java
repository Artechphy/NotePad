package com.example.android.notepad;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.LiveFolders;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowActivity;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import static org.junit.Assert.*;

/**
 * Harness-driven parity tests covering subtle Java-to-Kotlin behavioral,
 * visibility, nullability, and reflection contracts uncovered in Batch 1-3 audit.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class KotlinMigrationParityTest {
    private NotePadProvider provider;

    @After
    public void tearDown() {
        if (provider != null) {
            provider.getOpenHelperForTest().close();
            provider = null;
        }
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
}
