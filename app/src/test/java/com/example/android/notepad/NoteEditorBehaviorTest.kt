@file:Suppress("DEPRECATION")

package com.example.android.notepad

import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Spinner
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowActivity
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteEditorBehaviorTest {
    private var controller: ActivityController<out Activity>? = null

    @After
    fun tearDown() {
        closeEditor()
        val client = RuntimeEnvironment.getApplication().contentResolver
            .acquireContentProviderClient(NotePad.Notes.CONTENT_URI)
        if (client != null) {
            (client.localContentProvider as NotePadProvider).getOpenHelperForTest().close()
            client.release()
        }
    }

    private fun closeEditor() {
        controller?.pause()?.stop()?.destroy()
        controller = null
    }

    private fun seed(): Uri {
        val values = ContentValues().apply {
            put("title", "原标题")
            put("note", "原正文")
            put("created", 1000L)
            put("modified", 2000L)
        }
        return RuntimeEnvironment.getApplication().contentResolver
            .insert(NotePad.Notes.CONTENT_URI, values)!!
    }

    private fun openNote(action: String, uri: Uri): NoteEditor {
        val editor = Robolectric.buildActivity(NoteEditor::class.java, Intent(action, uri)).setup()
        controller = editor
        return editor.get()
    }

    private fun query(uri: Uri): Cursor =
        RuntimeEnvironment.getApplication().contentResolver.query(uri, null, null, null, null)!!

    @Test
    fun openingAndLeavingEditorDoesNotChangeModificationTime() {
        val uri = seed()
        openNote(Intent.ACTION_EDIT, uri)
        closeEditor()
        query(uri).use { row ->
            assertTrue(row.moveToFirst())
            assertEquals(2000L, row.getLong(row.getColumnIndexOrThrow("modified")))
        }
    }

    @Test
    fun bodyEditSavesAndIsPresentWhenEditorReopens() {
        val uri = seed()
        var editor = openNote(Intent.ACTION_EDIT, uri)
        editor.findViewById<EditText>(R.id.note).setText("修改后正文")
        closeEditor()
        query(uri).use { row ->
            assertTrue(row.moveToFirst())
            assertEquals(1000L, row.getLong(row.getColumnIndexOrThrow("created")))
            assertTrue(row.getLong(row.getColumnIndexOrThrow("modified")) > 2000L)
        }
        editor = openNote(Intent.ACTION_EDIT, uri)
        assertEquals("修改后正文", editor.findViewById<EditText>(R.id.note).text.toString())
    }

    @Test
    fun newNoteIsSavedAndAnAbandonedEmptyNoteIsDeleted() {
        var editor = openNote(Intent.ACTION_INSERT, NotePad.Notes.CONTENT_URI)
        editor.findViewById<EditText>(R.id.note).setText("期中实验笔记")
        editor.finish()
        closeEditor()
        editor = openNote(Intent.ACTION_INSERT, NotePad.Notes.CONTENT_URI)
        editor.finish()
        closeEditor()
        query(NotePad.Notes.CONTENT_URI).use { rows ->
            assertEquals(1, rows.count)
            assertTrue(rows.moveToFirst())
            assertEquals("期中实验笔记", rows.getString(rows.getColumnIndexOrThrow("note")))
        }
    }

    @Test
    fun titleEditorAlsoUpdatesModificationTime() {
        val uri = seed()
        val title = Robolectric.buildActivity(
            TitleEditor::class.java,
            Intent(TitleEditor.EDIT_TITLE_ACTION, uri)
        ).setup()
        controller = title
        title.get().findViewById<EditText>(R.id.title).setText("修改后标题")
        closeEditor()
        query(uri).use { row ->
            assertTrue(row.moveToFirst())
            assertEquals("修改后标题", row.getString(row.getColumnIndexOrThrow("title")))
            assertTrue(row.getLong(row.getColumnIndexOrThrow("modified")) > 2000L)
            assertEquals(1000L, row.getLong(row.getColumnIndexOrThrow("created")))
        }
    }

    private fun menuItem(editor: NoteEditor, id: Int): MenuItem {
        val popup = PopupMenu(editor, editor.findViewById(R.id.editor_more))
        editor.onCreateOptionsMenu(popup.menu)
        return popup.menu.findItem(id)
    }

    @Test
    fun titleOnlyNoteAndCategorySurviveSaveAndReopening() {
        var editor = openNote(Intent.ACTION_INSERT, NotePad.Notes.CONTENT_URI)
        editor.findViewById<EditText>(R.id.title).setText("只有标题的笔记")
        editor.findViewById<Spinner>(R.id.editor_category).setSelection(1)
        editor.findViewById<View>(R.id.editor_save).performClick()
        assertTrue(editor.isFinishing)
        closeEditor()
        val uri: Uri
        query(NotePad.Notes.CONTENT_URI).use { row ->
            assertEquals(1, row.count)
            assertTrue(row.moveToFirst())
            assertEquals("只有标题的笔记", row.getString(row.getColumnIndexOrThrow("title")))
            assertEquals("study", row.getString(row.getColumnIndexOrThrow("category")))
            uri = ContentUris.withAppendedId(
                NotePad.Notes.CONTENT_URI,
                row.getLong(row.getColumnIndexOrThrow("_id"))
            )
        }
        editor = openNote(Intent.ACTION_EDIT, uri)
        assertEquals("只有标题的笔记", editor.findViewById<EditText>(R.id.title).text.toString())
        assertEquals(1, editor.findViewById<Spinner>(R.id.editor_category).selectedItemPosition)
    }

    @Test
    fun recreatingNewEditorReusesDraftAndPreservesFields() {
        var editor = openNote(Intent.ACTION_INSERT, NotePad.Notes.CONTENT_URI)
        editor.findViewById<EditText>(R.id.title).setText("新草稿")
        editor.findViewById<EditText>(R.id.note).setText("旋转后仍在")
        editor.findViewById<Spinner>(R.id.editor_category).setSelection(2)
        controller!!.recreate()
        editor = controller!!.get() as NoteEditor
        assertEquals("新草稿", editor.findViewById<EditText>(R.id.title).text.toString())
        assertEquals("旋转后仍在", editor.findViewById<EditText>(R.id.note).text.toString())
        assertEquals(2, editor.findViewById<Spinner>(R.id.editor_category).selectedItemPosition)
        query(NotePad.Notes.CONTENT_URI).use { rows -> assertEquals(1, rows.count) }
    }

    @Test
    fun revertingRestoresTitleBodyAndCategoryTogether() {
        val uri = seed()
        val editor = openNote(Intent.ACTION_EDIT, uri)
        editor.findViewById<EditText>(R.id.title).setText("改动标题")
        editor.findViewById<EditText>(R.id.note).setText("改动正文")
        editor.findViewById<Spinner>(R.id.editor_category).setSelection(3)
        editor.onOptionsItemSelected(menuItem(editor, R.id.menu_revert))
        closeEditor()
        query(uri).use { row ->
            assertTrue(row.moveToFirst())
            assertEquals("原标题", row.getString(row.getColumnIndexOrThrow("title")))
            assertEquals("原正文", row.getString(row.getColumnIndexOrThrow("note")))
            assertEquals("uncategorized", row.getString(row.getColumnIndexOrThrow("category")))
        }
    }

    @Test
    fun exportSavesDraftAndLaunchesSystemDocumentIntent() {
        val editor = openNote(Intent.ACTION_INSERT, NotePad.Notes.CONTENT_URI)
        editor.findViewById<EditText>(R.id.title).setText("课程/笔记")
        editor.findViewById<EditText>(R.id.note).setText("正文")
        editor.onOptionsItemSelected(menuItem(editor, R.id.menu_export))
        val started = Shadow.extract<ShadowActivity>(editor).nextStartedActivityForResult
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, started.intent.action)
        assertEquals("text/plain", started.intent.type)
        assertEquals("课程_笔记.txt", started.intent.getStringExtra(Intent.EXTRA_TITLE))
        assertTrue(started.intent.hasCategory(Intent.CATEGORY_OPENABLE))
        query(NotePad.Notes.CONTENT_URI).use { row ->
            assertTrue(row.moveToFirst())
            assertEquals("正文", row.getString(row.getColumnIndexOrThrow("note")))
        }
        invokeOnActivityResult(editor, started.requestCode, Activity.RESULT_CANCELED, null)
        assertNull(ShadowToast.getTextOfLatestToast())
    }

    @Test
    @LooperMode(LooperMode.Mode.LEGACY)
    fun exportWritesChosenStreamAndReportsFailureWithoutChangingSourceTime() {
        Robolectric.getBackgroundThreadScheduler().pause()
        Robolectric.getForegroundThreadScheduler().pause()
        val uri = seed()
        val editor = openNote(Intent.ACTION_EDIT, uri)
        val target = Uri.parse("content://test-output/note.txt")
        val bytes = ByteArrayOutputStream()
        val shadowResolver = Shadow.extract<ShadowContentResolver>(
            RuntimeEnvironment.getApplication().contentResolver
        )
        shadowResolver.registerOutputStream(target, bytes)
        invokeOnActivityResult(editor, 701, Activity.RESULT_OK, Intent().setData(target))
        Robolectric.flushBackgroundThreadScheduler()
        ShadowLooper.idleMainLooper()
        assertTrue(bytes.toString("UTF-8").contains("原标题"))
        assertTrue(bytes.toString("UTF-8").contains("原正文"))
        assertEquals("笔记已导出", ShadowToast.getTextOfLatestToast())
        query(uri).use { row ->
            assertTrue(row.moveToFirst())
            assertEquals(2000L, row.getLong(row.getColumnIndexOrThrow("modified")))
        }
        shadowResolver.registerOutputStream(target, object : OutputStream() {
            override fun write(value: Int) {
                throw IOException("full")
            }
        })
        invokeOnActivityResult(editor, 701, Activity.RESULT_OK, Intent().setData(target))
        Robolectric.flushBackgroundThreadScheduler()
        ShadowLooper.idleMainLooper()
        assertEquals("导出失败，目标文件可能不完整，请重试", ShadowToast.getTextOfLatestToast())
    }

    private fun invokeOnActivityResult(
        editor: NoteEditor,
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        val method = NoteEditor::class.java.getDeclaredMethod(
            "onActivityResult",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Intent::class.java
        )
        method.isAccessible = true
        method.invoke(editor, requestCode, resultCode, data)
    }
}
