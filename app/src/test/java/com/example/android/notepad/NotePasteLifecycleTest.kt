@file:Suppress("DEPRECATION")

package com.example.android.notepad

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Spinner
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowClipboardManager
import org.robolectric.shadows.ShadowToast

/** Model the platform's external focus restriction; the editor and provider stay real. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [NotePasteLifecycleTest.FocusedClipboard::class])
class NotePasteLifecycleTest {
    @Implements(ClipboardManager::class)
    class FocusedClipboard : ShadowClipboardManager() {
        @Implementation
        override fun hasPrimaryClip(): Boolean {
            if (!focused) {
                deniedReads++
                return false
            }
            return super.hasPrimaryClip()
        }

        @Implementation
        override fun getPrimaryClip(): ClipData? {
            if (!focused) {
                deniedReads++
                return null
            }
            return super.getPrimaryClip()
        }

        companion object {
            var focused: Boolean = false
            var deniedReads: Int = 0
        }
    }

    private var controller: ActivityController<NoteEditor>? = null
    private lateinit var clipboard: ClipboardManager

    @Before
    fun setUp() {
        FocusedClipboard.focused = false
        FocusedClipboard.deniedReads = 0
        clipboard = RuntimeEnvironment.getApplication()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

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

    private fun seed(): Uri {
        val values = ContentValues().apply {
            put("title", "周会纪要")
            put("note", "讨论版本发布。")
            put("category", "work")
        }
        return RuntimeEnvironment.getApplication().contentResolver
            .insert(NotePad.Notes.CONTENT_URI, values)!!
    }

    private fun openPaste(state: Bundle?): NoteEditor {
        val created = Robolectric.buildActivity(
            NoteEditor::class.java,
            Intent(Intent.ACTION_PASTE, NotePad.Notes.CONTENT_URI)
        )
        controller = created
        created.create(state).start()
        if (state != null) created.restoreInstanceState(state)
        created.resume() // Deliberately no window focus yet.
        return created.get()
    }

    private fun focus() {
        FocusedClipboard.focused = true
        controller!!.get().onWindowFocusChanged(true)
    }

    private fun text(id: Int): String =
        controller!!.get().findViewById<EditText>(id).text.toString()

    private fun count(): Int {
        RuntimeEnvironment.getApplication().contentResolver
            .query(NotePad.Notes.CONTENT_URI, null, null, null, null)!!.use { rows ->
            return rows.count
        }
    }

    @Test
    fun notePasteWaitsForFocusAndCopiesAllThreeFields() {
        val original = seed()
        clipboard.setPrimaryClip(
            ClipData.newUri(RuntimeEnvironment.getApplication().contentResolver, "note", original)
        )
        openPaste(null)
        assertEquals("No clipboard read is allowed before focus", 0, FocusedClipboard.deniedReads)
        assertEquals("", text(R.id.title))
        focus()
        assertEquals("周会纪要", text(R.id.title))
        assertEquals("讨论版本发布。", text(R.id.note))
        assertEquals(
            3,
            controller!!.get().findViewById<Spinner>(R.id.editor_category).selectedItemPosition
        )
        assertEquals(2, count())
    }

    @Test
    fun focusChangesAndRecreationNeverOverwriteUserEdits() {
        seed()
        clipboard.setPrimaryClip(ClipData.newPlainText("text", "最初粘贴"))
        openPaste(null)
        focus()
        controller!!.get().findViewById<EditText>(R.id.note).setText("我自己的补充")
        clipboard.setPrimaryClip(ClipData.newPlainText("later", "后来复制的内容"))
        controller!!.get().onWindowFocusChanged(false)
        focus()
        assertEquals("我自己的补充", text(R.id.note))
        controller!!.recreate()
        focus()
        assertEquals("我自己的补充", text(R.id.note))
        assertEquals(2, count())
    }

    @Test
    fun pendingPasteSurvivesRecreationBeforeFirstFocus() {
        seed()
        clipboard.setPrimaryClip(ClipData.newPlainText("text", "重建后粘贴"))
        openPaste(null)
        val state = Bundle()
        controller!!.saveInstanceState(state).pause().stop().destroy()
        controller = null
        openPaste(state)
        assertEquals(0, FocusedClipboard.deniedReads)
        focus()
        assertEquals("重建后粘贴", text(R.id.note))
        assertEquals(2, count())
    }

    @Test
    fun plainTextPastePreservesUnicodeAndNewlines() {
        seed()
        clipboard.setPrimaryClip(ClipData.newPlainText("text", "外部文本\n第二行 📘"))
        openPaste(null)
        focus()
        assertEquals("外部文本\n第二行 📘", text(R.id.note))
        assertEquals("", text(R.id.title))
    }

    @Test
    fun emptyClipboardReportsItAndLeavingRemovesOnlyEmptyDraft() {
        seed()
        openPaste(null)
        focus()
        assertEquals("剪贴板为空或暂不可读取", ShadowToast.getTextOfLatestToast())
        controller!!.get().finish()
        controller!!.pause().stop().destroy()
        controller = null
        assertEquals(1, count())
    }

    @Test
    fun deletedSourceReportsItWithoutCreatingASavedCopy() {
        val original = seed()
        clipboard.setPrimaryClip(
            ClipData.newUri(RuntimeEnvironment.getApplication().contentResolver, "note", original)
        )
        RuntimeEnvironment.getApplication().contentResolver.delete(original, null, null)
        openPaste(null)
        focus()
        assertEquals("原笔记已删除或无法读取", ShadowToast.getTextOfLatestToast())
        assertEquals("", text(R.id.note))
        controller!!.get().finish()
        controller!!.pause().stop().destroy()
        controller = null
        assertEquals(0, count())
    }

    @Test
    fun listMenuDoesNotReadClipboardWhileWindowIsUnfocused() {
        val list = Robolectric.buildActivity(NotesList::class.java).setup().visible()
        try {
            list.windowFocusChanged(false)
            FocusedClipboard.deniedReads = 0
            val menu = PopupMenu(list.get(), list.get().findViewById(R.id.list_more)).menu
            list.get().onCreateOptionsMenu(menu)
            list.get().onPrepareOptionsMenu(menu)
            assertEquals(0, FocusedClipboard.deniedReads)
            assertFalse(menu.findItem(R.id.menu_paste).isEnabled)

            list.windowFocusChanged(true)
            FocusedClipboard.focused = true
            list.get().onPrepareOptionsMenu(menu)
            assertFalse(menu.findItem(R.id.menu_paste).isEnabled)
            clipboard.setPrimaryClip(ClipData.newPlainText("text", "可粘贴内容"))
            list.get().onPrepareOptionsMenu(menu)
            assertTrue(menu.findItem(R.id.menu_paste).isEnabled)

            list.windowFocusChanged(false)
            FocusedClipboard.focused = false
            list.get().onPrepareOptionsMenu(menu)
            assertEquals(0, FocusedClipboard.deniedReads)
            assertFalse(menu.findItem(R.id.menu_paste).isEnabled)
        } finally {
            list.pause().stop().destroy()
        }
    }
}
