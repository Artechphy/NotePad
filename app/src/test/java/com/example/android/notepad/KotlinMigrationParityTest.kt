@file:Suppress("DEPRECATION")

package com.example.android.notepad

import android.app.Activity
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ProviderInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.LiveFolders
import android.view.ContextMenu
import android.view.View
import android.widget.AdapterView
import android.widget.EditText
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.fakes.RoboMenuItem
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowActivity
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast
import java.io.ByteArrayOutputStream
import java.lang.reflect.Modifier

/**
 * Harness-driven parity tests covering subtle Java-to-Kotlin behavioral,
 * visibility, nullability, lifecycle, and reflection contracts across migration batches.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.LEGACY)
class KotlinMigrationParityTest {
    private var provider: NotePadProvider? = null

    @After
    fun tearDown() {
        provider?.getOpenHelperForTest()?.close()
        provider = null
        val client = RuntimeEnvironment.getApplication().contentResolver
            .acquireContentProviderClient(NotePad.Notes.CONTENT_URI)
        if (client != null) {
            (client.localContentProvider as NotePadProvider).getOpenHelperForTest().close()
            client.release()
        }
    }

    private fun drainLoader() {
        repeat(3) {
            Robolectric.flushForegroundThreadScheduler()
            Robolectric.flushBackgroundThreadScheduler()
        }
        Robolectric.flushForegroundThreadScheduler()
    }

    private fun insertNote(title: String, body: String, modified: Long): Uri {
        val values = ContentValues().apply {
            put(NotePad.Notes.COLUMN_NAME_TITLE, title)
            put(NotePad.Notes.COLUMN_NAME_NOTE, body)
            put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, modified)
        }
        return RuntimeEnvironment.getApplication().contentResolver
            .insert(NotePad.Notes.CONTENT_URI, values)!!
    }

    @Test
    fun titleEditorOnCreateRemainsPublicAndXmlOnClickFinishesActivity() {
        val onCreate = TitleEditor::class.java.getDeclaredMethod("onCreate", Bundle::class.java)
        assertTrue(
            "TitleEditor.onCreate must remain public after Kotlin override",
            Modifier.isPublic(onCreate.modifiers)
        )

        val values = ContentValues().apply { put(NotePad.Notes.COLUMN_NAME_TITLE, "原标题") }
        val uri = RuntimeEnvironment.getApplication().contentResolver
            .insert(NotePad.Notes.CONTENT_URI, values)
        val controller = Robolectric.buildActivity(
            TitleEditor::class.java,
            Intent(TitleEditor.EDIT_TITLE_ACTION, uri)
        ).setup()
        try {
            val editor = controller.get()
            editor.findViewById<View>(R.id.ok).performClick()
            assertTrue(
                "android:onClick=\"onClickOk\" must invoke TitleEditor.onClickOk",
                editor.isFinishing
            )
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun noteExportDocumentToleratesNullPartsLikeJavaStringConcat() {
        val rendered = NoteExport.document(null, null, null, null)
        assertEquals("null\n\n分类：null\n修改时间：null\n\nnull\n", rendered)
    }

    @Test
    fun trimMatchesJavaStringTrimSemanticsForControlCharsAndFullWidthSpaces() {
        assertEquals("", NoteSearch.normalize("\u0000\u0007\u001B"))
        assertEquals("正文", NoteText.title("\u0000\u0007", "正文"))
        assertEquals("　全角保留　", NoteSearch.normalize("　全角保留　"))
        assertEquals("　全角　", NoteText.preview("　全角　"))
    }

    @Test
    fun notePadProviderUpdateWithNullValuesPreservesNullPointerException() {
        val instance = NotePadProvider()
        provider = instance
        val info = ProviderInfo().apply { authority = NotePad.AUTHORITY }
        instance.attachInfo(RuntimeEnvironment.getApplication(), info)
        val uri = instance.insert(NotePad.Notes.CONTENT_URI, ContentValues())
        assertThrows(NullPointerException::class.java) { instance.update(uri, null, null, null) }
    }

    @Test
    fun notesLiveFolderReturnsExpectedResultForSupportedAndUnsupportedActions() {
        val supported = Robolectric.buildActivity(
            NotesLiveFolder::class.java,
            Intent(LiveFolders.ACTION_CREATE_LIVE_FOLDER)
        ).create()
        try {
            val shadow = Shadow.extract<ShadowActivity>(supported.get())
            assertTrue(supported.get().isFinishing)
            assertEquals(Activity.RESULT_OK, shadow.resultCode)
            assertNotNull(shadow.resultIntent)
            assertEquals(NotePad.Notes.LIVE_FOLDER_URI, shadow.resultIntent.data)
        } finally {
            supported.destroy()
        }

        val unsupported = Robolectric.buildActivity(
            NotesLiveFolder::class.java,
            Intent(Intent.ACTION_VIEW)
        ).create()
        try {
            val shadow = Shadow.extract<ShadowActivity>(unsupported.get())
            assertTrue(unsupported.get().isFinishing)
            assertEquals(Activity.RESULT_CANCELED, shadow.resultCode)
        } finally {
            unsupported.destroy()
        }
    }

    @Test
    fun notesListLoaderIsStaticNonInnerAndShowsErrorOnNullCursor() {
        val loaderClass = Class.forName("com.example.android.notepad.NotesList\$NotesLoader")
        assertTrue(
            "NotesLoader must remain static (non-inner) to avoid leaking NotesList",
            Modifier.isStatic(loaderClass.modifiers)
        )
        for (field in loaderClass.declaredFields) {
            assertNotEquals(
                "NotesLoader must not hold synthetic outer NotesList reference",
                NotesList::class.java,
                field.type
            )
        }
        val constructors = loaderClass.declaredConstructors
        assertEquals(1, constructors.size)
        assertEquals(Context::class.java, constructors[0].parameterTypes[0])

        Robolectric.getBackgroundThreadScheduler().pause()
        Robolectric.getForegroundThreadScheduler().pause()
        val controller = Robolectric.buildActivity(NotesList::class.java).setup()
        try {
            drainLoader()
            val activity = controller.get()
            activity.onLoadFinished(null, null)
            assertEquals(
                activity.getString(R.string.notes_load_error),
                activity.findViewById<TextView>(android.R.id.empty).text.toString()
            )
            assertEquals(
                activity.getString(R.string.note_count, 0),
                activity.findViewById<TextView>(R.id.note_count).text.toString()
            )
        } finally {
            controller.pause().stop().destroy()
            drainLoader()
        }
    }

    @Test
    fun notesListViewBinderMatchesJavaTrimAndEmptyPreviewSemantics() {
        Robolectric.getBackgroundThreadScheduler().pause()
        Robolectric.getForegroundThreadScheduler().pause()
        insertNote("\u0000\u0007", "", 1704139440000L)
        insertNote("　", "全角标题", 1704139441000L)
        val controller = Robolectric.buildActivity(NotesList::class.java).setup()
        try {
            drainLoader()
            val activity = controller.get()
            assertEquals(2, activity.listAdapter.count)

            val fullWidthRow = activity.listAdapter.getView(0, null, activity.listView)
            assertEquals(
                "　",
                fullWidthRow.findViewById<TextView>(android.R.id.text1).text.toString()
            )

            val controlRow = activity.listAdapter.getView(1, null, activity.listView)
            assertEquals(
                activity.getString(R.string.untitled_note),
                controlRow.findViewById<TextView>(android.R.id.text1).text.toString()
            )
            assertEquals(
                activity.getString(R.string.empty_preview),
                controlRow.findViewById<TextView>(R.id.note_preview).text.toString()
            )
        } finally {
            controller.pause().stop().destroy()
            drainLoader()
        }
    }

    @Test
    fun notesListItemClickSupportsEditPickAndGetContent() {
        Robolectric.getBackgroundThreadScheduler().pause()
        Robolectric.getForegroundThreadScheduler().pause()
        val noteUri = insertNote("点击测试", "正文", 1704139440000L)
        val noteId = ContentUris.parseId(noteUri)

        val editController = Robolectric.buildActivity(NotesList::class.java).setup()
        try {
            drainLoader()
            val activity = editController.get()
            invokeOnListItemClick(activity, activity.listView, null, 0, noteId)
            val shadow = Shadow.extract<ShadowActivity>(activity)
            val started = shadow.nextStartedActivity
            assertNotNull(started)
            assertEquals(Intent.ACTION_EDIT, started.action)
            assertEquals(noteUri, started.data)
            assertEquals(NoteEditor::class.java.name, started.component!!.className)
        } finally {
            editController.pause().stop().destroy()
            drainLoader()
        }

        val pickController = Robolectric.buildActivity(
            NotesList::class.java,
            Intent(Intent.ACTION_PICK, NotePad.Notes.CONTENT_URI)
        ).setup()
        try {
            drainLoader()
            val activity = pickController.get()
            invokeOnListItemClick(activity, activity.listView, null, 0, noteId)
            val shadow = Shadow.extract<ShadowActivity>(activity)
            assertEquals(Activity.RESULT_OK, shadow.resultCode)
            assertNotNull(shadow.resultIntent)
            assertEquals(noteUri, shadow.resultIntent.data)
            assertFalse("Original Java ACTION_PICK path does not auto-finish", activity.isFinishing)
        } finally {
            pickController.pause().stop().destroy()
            drainLoader()
        }
    }

    @Test
    fun notesListOptionsAndContextMenuHandleCopyDeleteAndBadMenuInfo() {
        Robolectric.getBackgroundThreadScheduler().pause()
        Robolectric.getForegroundThreadScheduler().pause()
        val noteUri = insertNote("上下文笔记", "内容", 1704139440000L)
        val noteId = ContentUris.parseId(noteUri)

        val controller = Robolectric.buildActivity(NotesList::class.java).setup()
        try {
            drainLoader()
            val activity = controller.get()
            val shadow = Shadow.extract<ShadowActivity>(activity)

            assertTrue(activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_add)))
            val addIntent = shadow.nextStartedActivity
            assertEquals(Intent.ACTION_INSERT, addIntent.action)
            assertTrue(activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_paste)))
            val pasteIntent = shadow.nextStartedActivity
            assertEquals(Intent.ACTION_PASTE, pasteIntent.action)

            val badInfo = object : ContextMenu.ContextMenuInfo {}
            activity.onCreateContextMenu(null, null, badInfo)
            val badItem = MenuItemWithInfo(R.id.context_delete, badInfo)
            assertFalse(activity.onContextItemSelected(badItem))

            val validInfo = AdapterView.AdapterContextMenuInfo(View(activity), 0, noteId)
            assertTrue(activity.onContextItemSelected(MenuItemWithInfo(R.id.context_open, validInfo)))
            val openIntent = shadow.nextStartedActivity
            assertEquals(Intent.ACTION_EDIT, openIntent.action)
            assertEquals(noteUri, openIntent.data)

            assertTrue(activity.onContextItemSelected(MenuItemWithInfo(R.id.context_copy, validInfo)))
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertNotNull(clipboard.primaryClip)
            assertEquals(noteUri, clipboard.primaryClip!!.getItemAt(0).uri)

            assertTrue(activity.onContextItemSelected(MenuItemWithInfo(R.id.context_delete, validInfo)))
            RuntimeEnvironment.getApplication().contentResolver
                .query(NotePad.Notes.CONTENT_URI, null, null, null, null)!!.use { cursor ->
                assertEquals(0, cursor.count)
            }
        } finally {
            controller.pause().stop().destroy()
            drainLoader()
        }
    }

    @Test
    fun noteEditorEarlyExitOnInvalidUriSurvivesLifecycleWithoutLateinitCrash() {
        val badIntents = arrayOf(
            Intent(Intent.ACTION_MAIN, NotePad.Notes.CONTENT_URI),
            Intent(Intent.ACTION_EDIT, ContentUris.withAppendedId(NotePad.Notes.CONTENT_URI, 999999L))
        )
        for (intent in badIntents) {
            val controller = Robolectric.buildActivity(NoteEditor::class.java, intent).setup()
            try {
                val editor = controller.get()
                assertTrue("Early exit must finish activity on invalid intent", editor.isFinishing)
                assertEquals(editor.getString(R.string.error_message), ShadowToast.getTextOfLatestToast())

                editor.onWindowFocusChanged(true)
                editor.onBackPressed()
                val popup = PopupMenu(editor, editor.findViewById(R.id.editor_more))
                editor.onCreateOptionsMenu(popup.menu)
                assertFalse(editor.onPrepareOptionsMenu(popup.menu))
                val state = Bundle()
                controller.saveInstanceState(state)
                assertFalse(state.containsKey("note_uri"))
            } finally {
                controller.pause().stop().destroy()
            }
        }
    }

    @Test
    fun noteExportTaskRetainsAcrossConfigurationChangeAndDeliversResultOnce() {
        Robolectric.getBackgroundThreadScheduler().pause()
        Robolectric.getForegroundThreadScheduler().pause()
        ShadowToast.reset()
        val noteUri = insertNote("旋转导出", "跨配置变更正文", 1704139440000L)
        val target = Uri.parse("content://test-output/rotated.txt")
        val bytes = ByteArrayOutputStream()
        val shadowResolver = Shadow.extract<ShadowContentResolver>(
            RuntimeEnvironment.getApplication().contentResolver
        )
        shadowResolver.registerOutputStream(target, bytes)

        val controller = Robolectric.buildActivity(
            NoteEditor::class.java,
            Intent(Intent.ACTION_EDIT, noteUri)
        ).setup()
        try {
            val original = controller.get()
            invokeOnActivityResult(original, 701, Activity.RESULT_OK, Intent().setData(target))
            assertEquals(
                original.getString(R.string.exporting),
                original.findViewById<TextView>(R.id.save_status).text.toString()
            )

            val rotated = Configuration(original.resources.configuration).apply {
                orientation = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    Configuration.ORIENTATION_PORTRAIT
                } else {
                    Configuration.ORIENTATION_LANDSCAPE
                }
            }
            controller.configurationChange(rotated)
            val recreated = controller.get()
            assertNotSame(original, recreated)
            assertTrue(
                "Recreated NoteEditor must retain the in-flight NoteExportTask",
                recreated.lastNonConfigurationInstance is NoteExportTask
            )

            Robolectric.flushBackgroundThreadScheduler()
            ShadowLooper.idleMainLooper()
            assertTrue(bytes.toString("UTF-8").contains("旋转导出"))
            assertTrue(bytes.toString("UTF-8").contains("跨配置变更正文"))
            assertEquals(
                recreated.getString(R.string.saved),
                recreated.findViewById<TextView>(R.id.save_status).text.toString()
            )
            assertEquals(recreated.getString(R.string.export_success), ShadowToast.getTextOfLatestToast())
            assertEquals(1, ShadowToast.shownToastCount())

            val rotatedBack = Configuration(recreated.resources.configuration).apply {
                orientation = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    Configuration.ORIENTATION_PORTRAIT
                } else {
                    Configuration.ORIENTATION_LANDSCAPE
                }
            }
            controller.configurationChange(rotatedBack)
            assertEquals(1, ShadowToast.shownToastCount())
        } finally {
            Robolectric.flushBackgroundThreadScheduler()
            ShadowLooper.idleMainLooper()
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun noteEditorEmptyCheckMatchesJavaTrimSemanticsForControlAndFullWidthSpaces() {
        val controller = Robolectric.buildActivity(
            NoteEditor::class.java,
            Intent(Intent.ACTION_INSERT, NotePad.Notes.CONTENT_URI)
        ).setup()
        try {
            val editor = controller.get()
            editor.findViewById<EditText>(R.id.title).setText("\u0000\u0007")
            editor.findViewById<EditText>(R.id.note).setText("\u001B")
            editor.findViewById<View>(R.id.editor_save).performClick()
            assertFalse(
                "Control-char-only draft must be treated as empty like Java String.trim()",
                editor.isFinishing
            )
            assertEquals(editor.getString(R.string.nothing_to_save), ShadowToast.getTextOfLatestToast())

            editor.findViewById<EditText>(R.id.title).setText("　")
            editor.findViewById<View>(R.id.editor_save).performClick()
            assertTrue(
                "Full-width space must be treated as non-empty like Java String.trim()",
                editor.isFinishing
            )
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun noteEditorMenuDeleteDialogAndNewNoteRevertRemoveDraft() {
        val noteUri = insertNote("待删笔记", "确认删除测试", 1704139440000L)
        val editController = Robolectric.buildActivity(
            NoteEditor::class.java,
            Intent(Intent.ACTION_EDIT, noteUri)
        ).setup()
        try {
            val editor = editController.get()
            assertTrue(editor.onOptionsItemSelected(RoboMenuItem(R.id.menu_delete)))
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertNotNull("Editor delete must show confirmation dialog", dialog)
            assertTrue(dialog.isShowing)
            assertFalse(editor.isFinishing)

            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            assertTrue(editor.isFinishing)
            RuntimeEnvironment.getApplication().contentResolver
                .query(noteUri, null, null, null, null)!!.use { cursor ->
                assertEquals(0, cursor.count)
            }
        } finally {
            editController.pause().stop().destroy()
        }

        val newController = Robolectric.buildActivity(
            NoteEditor::class.java,
            Intent(Intent.ACTION_INSERT, NotePad.Notes.CONTENT_URI)
        ).setup()
        try {
            val editor = newController.get()
            editor.findViewById<EditText>(R.id.note).setText("临时草稿")
            assertTrue(editor.onOptionsItemSelected(RoboMenuItem(R.id.menu_revert)))
            assertTrue(editor.isFinishing)
            RuntimeEnvironment.getApplication().contentResolver
                .query(NotePad.Notes.CONTENT_URI, null, null, null, null)!!.use { cursor ->
                assertEquals(0, cursor.count)
            }
        } finally {
            newController.pause().stop().destroy()
        }
    }

    private fun invokeOnListItemClick(
        activity: NotesList,
        listView: ListView?,
        view: View?,
        position: Int,
        id: Long
    ) {
        val method = NotesList::class.java.getDeclaredMethod(
            "onListItemClick",
            ListView::class.java,
            View::class.java,
            Int::class.javaPrimitiveType,
            Long::class.javaPrimitiveType
        )
        method.isAccessible = true
        method.invoke(activity, listView, view, position, id)
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

    private class MenuItemWithInfo(
        itemId: Int,
        private val info: ContextMenu.ContextMenuInfo
    ) : RoboMenuItem(itemId) {
        override fun getMenuInfo(): ContextMenu.ContextMenuInfo = info
    }
}
