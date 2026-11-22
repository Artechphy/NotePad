@file:Suppress("DEPRECATION")

package com.example.android.notepad

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Spinner
import android.widget.TextView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteEditorSaveFailureTest {
    private var controller: ActivityController<NoteEditor>? = null
    private lateinit var source: Uri

    @After
    fun tearDown() {
        controller?.pause()?.stop()?.destroy()
        val client = RuntimeEnvironment.getApplication().contentResolver
            .acquireContentProviderClient(NotePad.Notes.CONTENT_URI)
        if (client != null) {
            (client.localContentProvider as NotePadProvider).getOpenHelperForTest().close()
            client.release()
        }
    }

    private fun editRemovedSource(): NoteEditor {
        val values = ContentValues().apply {
            put("title", "原笔记")
            put("note", "原正文")
        }
        source = RuntimeEnvironment.getApplication().contentResolver
            .insert(NotePad.Notes.CONTENT_URI, values)!!
        val created = Robolectric.buildActivity(
            NoteEditor::class.java,
            Intent(Intent.ACTION_EDIT, source)
        ).setup()
        controller = created
        val editor = created.get()
        editor.findViewById<EditText>(R.id.title).setText("未保存标题")
        editor.findViewById<EditText>(R.id.note).setText("未保存正文\n第二行📘")
        editor.findViewById<Spinner>(R.id.editor_category).setSelection(2)
        RuntimeEnvironment.getApplication().contentResolver.delete(source, null, null)
        return editor
    }

    private fun assertInput(editor: NoteEditor) {
        assertEquals("未保存标题", editor.findViewById<EditText>(R.id.title).text.toString())
        assertEquals("未保存正文\n第二行📘", editor.findViewById<EditText>(R.id.note).text.toString())
        assertEquals(2, editor.findViewById<Spinner>(R.id.editor_category).selectedItemPosition)
    }

    @Test
    fun toolbarBackKeepsFailedInputVisible() {
        val editor = editRemovedSource()
        editor.findViewById<View>(R.id.editor_back).performClick()
        assertFalse("Failed save must not close the editor", editor.isFinishing)
        assertInput(editor)
        assertEquals("保存失败，请重试", editor.findViewById<TextView>(R.id.save_status).text)
    }

    @Test
    fun systemBackKeepsFailedInputVisible() {
        val editor = editRemovedSource()
        editor.onBackPressed()
        assertFalse("System Back must also guard failed saves", editor.isFinishing)
        assertInput(editor)
    }

    @Test
    fun backgroundFailureIsVisibleAndInputRemains() {
        val editor = editRemovedSource()
        controller!!.pause().resume()
        assertInput(editor)
        assertEquals("自动保存失败，内容仍保留，请返回重试", ShadowToast.getTextOfLatestToast())
        assertEquals("保存失败，请重试", editor.findViewById<TextView>(R.id.save_status).text)
    }

    @Test
    fun missingSourceCanRecreateWithoutDiscardingInput() {
        editRemovedSource()
        controller!!.recreate()
        val editor = controller!!.get()
        assertFalse("A recoverable editor must open even when the source is missing", editor.isFinishing)
        assertInput(editor)
        assertEquals("保存失败，请重试", editor.findViewById<TextView>(R.id.save_status).text)
        RuntimeEnvironment.getApplication().contentResolver
            .query(NotePad.Notes.CONTENT_URI, null, null, null, null)!!.use { rows ->
            assertEquals("Recovery must not silently recreate a deleted source", 0, rows.count)
        }
    }

    @Test
    fun explicitSaveAsNewRecoversFieldsAndPreservesOtherNotes() {
        val other = ContentValues().apply {
            put("title", "保留笔记")
            put("note", "不应被覆盖")
        }
        val otherUri = RuntimeEnvironment.getApplication().contentResolver
            .insert(NotePad.Notes.CONTENT_URI, other)!!
        val editor = editRemovedSource()
        editor.findViewById<View>(R.id.editor_back).performClick()
        val popup = PopupMenu(editor, editor.findViewById(R.id.editor_more))
        editor.onCreateOptionsMenu(popup.menu)
        editor.onPrepareOptionsMenu(popup.menu)
        assertTrue(popup.menu.findItem(R.id.menu_save_as_new).isVisible)
        editor.onOptionsItemSelected(popup.menu.findItem(R.id.menu_save_as_new))
        assertTrue(editor.isFinishing)
        controller!!.pause().stop().destroy()
        controller = null
        RuntimeEnvironment.getApplication().contentResolver
            .query(NotePad.Notes.CONTENT_URI, null, null, null, null)!!.use { rows ->
            assertEquals(2, rows.count)
            var recovered = false
            while (rows.moveToNext()) {
                if ("未保存标题" == rows.getString(rows.getColumnIndexOrThrow("title"))) {
                    recovered = true
                    assertEquals("未保存正文\n第二行📘", rows.getString(rows.getColumnIndexOrThrow("note")))
                    assertEquals("life", rows.getString(rows.getColumnIndexOrThrow("category")))
                }
            }
            assertTrue(recovered)
        }
        RuntimeEnvironment.getApplication().contentResolver
            .query(otherUri, null, null, null, null)!!.use { row ->
            assertTrue(row.moveToFirst())
            assertEquals("不应被覆盖", row.getString(row.getColumnIndexOrThrow("note")))
        }
    }

    @Test
    fun returnCanRetryAfterSourceIsRestored() {
        val editor = editRemovedSource()
        editor.findViewById<View>(R.id.editor_back).performClick()
        assertFalse(editor.isFinishing)
        val original = ContentValues().apply {
            put("_id", source.lastPathSegment!!.toLong())
            put("title", "原笔记")
            put("note", "原正文")
        }
        RuntimeEnvironment.getApplication().contentResolver.insert(NotePad.Notes.CONTENT_URI, original)
        editor.findViewById<View>(R.id.editor_back).performClick()
        assertTrue(editor.isFinishing)
        RuntimeEnvironment.getApplication().contentResolver
            .query(source, null, null, null, null)!!.use { row ->
            assertTrue(row.moveToFirst())
            assertEquals("未保存标题", row.getString(row.getColumnIndexOrThrow("title")))
            assertEquals("未保存正文\n第二行📘", row.getString(row.getColumnIndexOrThrow("note")))
            assertEquals("life", row.getString(row.getColumnIndexOrThrow("category")))
        }
    }

    @Test
    fun returningFromEmptyNewDraftStillRemovesIt() {
        val created = Robolectric.buildActivity(
            NoteEditor::class.java,
            Intent(Intent.ACTION_INSERT, NotePad.Notes.CONTENT_URI)
        ).setup()
        controller = created
        val editor = created.get()
        editor.findViewById<View>(R.id.editor_back).performClick()
        assertTrue(editor.isFinishing)
        RuntimeEnvironment.getApplication().contentResolver
            .query(NotePad.Notes.CONTENT_URI, null, null, null, null)!!.use { rows ->
            assertEquals(0, rows.count)
        }
    }
}
