@file:Suppress("DEPRECATION")

package com.example.android.notepad

import android.content.ContentValues
import android.net.Uri
import android.widget.SearchView
import android.widget.TextView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
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

/** Activity/component checks in an isolated JVM sandbox, not emulator UI acceptance. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.LEGACY)
class NotesListTest {
    private lateinit var controller: ActivityController<NotesList>
    private lateinit var activity: NotesList

    @Before
    fun setUp() {
        // Real Android queues loader work. Do not run a refresh reentrantly while a cursor
        // is dispatching a change notification (LEGACY's default automatic execution).
        Robolectric.getBackgroundThreadScheduler().pause()
        Robolectric.getForegroundThreadScheduler().pause()
        insert("安卓课程", "界面实验", 1704139440000L)
        insert("购物", "安卓设备", 1704139440000L)
        insert("周末", "阅读", 0L)
        controller = Robolectric.buildActivity(NotesList::class.java).setup()
        activity = controller.get()
        drainQueries()
    }

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
        drainQueries()
        val client = RuntimeEnvironment.getApplication().contentResolver
            .acquireContentProviderClient(NotePad.Notes.CONTENT_URI)
        if (client != null) {
            (client.localContentProvider as NotePadProvider).getOpenHelperForTest().close()
            client.release()
        }
    }

    private fun insert(title: String, body: String, modified: Long): Uri? {
        val values = ContentValues().apply {
            put("title", title)
            put("note", body)
            put("modified", modified)
        }
        return RuntimeEnvironment.getApplication().contentResolver
            .insert(NotePad.Notes.CONTENT_URI, values)
    }

    private fun drainQueries() {
        repeat(3) {
            Robolectric.flushForegroundThreadScheduler()
            Robolectric.flushBackgroundThreadScheduler()
        }
        Robolectric.flushForegroundThreadScheduler()
    }

    private fun search(): SearchView = activity.findViewById(R.id.search_notes)

    private fun query(text: String) {
        search().setQuery(text, false)
        drainQueries()
    }

    private fun titleAt(position: Int): String {
        return activity.listAdapter.getView(position, null, activity.listView)
            .findViewById<TextView>(android.R.id.text1).text.toString()
    }

    @Test
    fun opensWithStableSortAndReadableTimestamp() {
        assertEquals(3, activity.listAdapter.count)
        assertEquals("购物", titleAt(0)) // Equal timestamps use descending IDs.
        assertEquals("安卓课程", titleAt(1))
        val modified = activity.listAdapter.getView(0, null, activity.listView)
            .findViewById<TextView>(R.id.note_modified)
        assertTrue(modified.text.toString().matches(Regex("修改于 2024-01-0[12] \\d{2}:04")))
        val unknown = activity.listAdapter.getView(2, null, activity.listView)
            .findViewById<TextView>(R.id.note_modified)
        assertEquals("时间未知", unknown.text.toString())
    }

    @Test
    fun searchesBothFieldsAndClearsBackToAllNotes() {
        query("安卓")
        assertEquals(2, activity.listAdapter.count)
        assertEquals("购物", titleAt(0))
        query("不存在")
        assertEquals(0, activity.listAdapter.count)
        assertEquals(
            "没有匹配的笔记",
            activity.findViewById<TextView>(android.R.id.empty).text.toString()
        )
        query("")
        assertEquals(3, activity.listAdapter.count)
    }

    @Test
    fun rapidInputDoesNotRestoreOlderResults() {
        search().setQuery("安卓", false)
        search().setQuery("购物", false)
        search().setQuery("周末", false)
        drainQueries()
        assertEquals(1, activity.listAdapter.count)
        assertEquals("周末", titleAt(0))
    }

    @Test
    fun recreationPreservesKeywordAndFilter() {
        query("设备")
        controller.recreate()
        activity = controller.get()
        drainQueries()
        assertEquals("设备", search().query.toString())
        assertEquals(1, activity.listAdapter.count)
        assertEquals("购物", titleAt(0))
    }

    @Test
    fun providerChangesRefreshTheCurrentSearch() {
        query("设备")
        assertEquals(1, activity.listAdapter.count)
        val change = ContentValues().apply { put("note", "水果") }
        RuntimeEnvironment.getApplication().contentResolver.update(
            NotePad.Notes.CONTENT_URI,
            change,
            "title = ?",
            arrayOf("购物")
        )
        drainQueries()
        assertEquals(0, activity.listAdapter.count)
        assertEquals("设备", search().query.toString())
    }

    @Test
    fun categoryFilterSurvivesClearingSearchAndRecreation() {
        val values = ContentValues().apply { put("category", "study") }
        RuntimeEnvironment.getApplication().contentResolver.update(
            NotePad.Notes.CONTENT_URI,
            values,
            "title = ?",
            arrayOf("安卓课程")
        )
        activity.findViewById<android.view.View>(R.id.category_filters).findViewWithTag<android.view.View>("study").performClick()
        query("安卓")
        assertEquals(1, activity.listAdapter.count)
        assertEquals("安卓课程", titleAt(0))
        query("")
        assertEquals(1, activity.listAdapter.count)
        controller.recreate()
        activity = controller.get()
        drainQueries()
        assertTrue(activity.findViewById<android.view.View>(R.id.category_filters).findViewWithTag<android.view.View>("study").isSelected)
        assertEquals(1, activity.listAdapter.count)
        activity.findViewById<android.view.View>(R.id.new_note).performClick()
        val intent = (Shadow.extract<ShadowActivity>(activity)).nextStartedActivity
        assertEquals("study", intent.getStringExtra("category"))
    }

    @Test
    fun emptyCategoryHasSpecificFeedbackAndPreviewBindsTheBody() {
        val row = activity.listAdapter.getView(0, null, activity.listView)
        assertEquals("安卓设备", row.findViewById<TextView>(R.id.note_preview).text.toString())
        assertEquals("未分类", row.findViewById<TextView>(R.id.note_category).text.toString())
        activity.findViewById<android.view.View>(R.id.category_filters).findViewWithTag<android.view.View>("work").performClick()
        drainQueries()
        assertEquals(0, activity.listAdapter.count)
        assertEquals(
            "这个分类还没有笔记",
            activity.findViewById<TextView>(android.R.id.empty).text.toString()
        )
    }
}
