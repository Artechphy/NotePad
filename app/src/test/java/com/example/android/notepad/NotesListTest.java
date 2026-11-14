package com.example.android.notepad;

import android.content.ContentValues;
import android.content.ContentProviderClient;
import android.net.Uri;
import android.widget.SearchView;
import android.widget.TextView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

/** Activity/component checks in an isolated JVM sandbox, not emulator UI acceptance. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@LooperMode(LooperMode.Mode.LEGACY)
public class NotesListTest {
    private ActivityController<NotesList> controller;
    private NotesList activity;

    @Before public void setUp() {
        // Real Android queues loader work. Do not run a refresh reentrantly while a cursor
        // is dispatching a change notification (LEGACY's default automatic execution).
        Robolectric.getBackgroundThreadScheduler().pause();
        Robolectric.getForegroundThreadScheduler().pause();
        insert("安卓课程", "界面实验", 1704139440000L);
        insert("购物", "安卓设备", 1704139440000L);
        insert("周末", "阅读", 0L);
        controller = Robolectric.buildActivity(NotesList.class).setup();
        activity = controller.get();
        drainQueries();
    }

    @After public void tearDown() {
        controller.pause().stop().destroy();
        drainQueries();
        ContentProviderClient client = RuntimeEnvironment.getApplication().getContentResolver()
                .acquireContentProviderClient(NotePad.Notes.CONTENT_URI);
        if (client != null) {
            ((NotePadProvider) client.getLocalContentProvider()).getOpenHelperForTest().close();
            client.release();
        }
    }

    private Uri insert(String title, String body, long modified) {
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("note", body);
        values.put("modified", modified);
        return RuntimeEnvironment.getApplication().getContentResolver()
                .insert(NotePad.Notes.CONTENT_URI, values);
    }

    private void drainQueries() {
        for (int i = 0; i < 3; i++) {
            Robolectric.flushForegroundThreadScheduler();
            Robolectric.flushBackgroundThreadScheduler();
        }
        Robolectric.flushForegroundThreadScheduler();
    }

    private SearchView search() {
        return (SearchView) activity.findViewById(R.id.search_notes);
    }

    private void query(String text) {
        search().setQuery(text, false);
        drainQueries();
    }

    private String titleAt(int position) {
        return ((TextView) activity.getListAdapter().getView(position, null,
                activity.getListView()).findViewById(android.R.id.text1)).getText().toString();
    }

    @Test public void opensWithStableSortAndReadableTimestamp() {
        assertEquals(3, activity.getListAdapter().getCount());
        assertEquals("购物", titleAt(0)); // Equal timestamps use descending IDs.
        assertEquals("安卓课程", titleAt(1));
        TextView modified = (TextView) activity.getListAdapter().getView(0, null,
                activity.getListView()).findViewById(R.id.note_modified);
        assertTrue(modified.getText().toString().matches("修改于 2024-01-0[12] \\d{2}:04"));
        TextView unknown = (TextView) activity.getListAdapter().getView(2, null,
                activity.getListView()).findViewById(R.id.note_modified);
        assertEquals("时间未知", unknown.getText().toString());
    }

    @Test public void searchesBothFieldsAndClearsBackToAllNotes() {
        query("安卓");
        assertEquals(2, activity.getListAdapter().getCount());
        assertEquals("购物", titleAt(0));
        query("不存在");
        assertEquals(0, activity.getListAdapter().getCount());
        assertEquals("没有匹配的笔记", ((TextView) activity.findViewById(android.R.id.empty))
                .getText().toString());
        query("");
        assertEquals(3, activity.getListAdapter().getCount());
    }

    @Test public void rapidInputDoesNotRestoreOlderResults() {
        search().setQuery("安卓", false);
        search().setQuery("购物", false);
        search().setQuery("周末", false);
        drainQueries();
        assertEquals(1, activity.getListAdapter().getCount());
        assertEquals("周末", titleAt(0));
    }

    @Test public void recreationPreservesKeywordAndFilter() {
        query("设备");
        controller.recreate();
        activity = controller.get();
        drainQueries();
        assertEquals("设备", search().getQuery().toString());
        assertEquals(1, activity.getListAdapter().getCount());
        assertEquals("购物", titleAt(0));
    }

    @Test public void providerChangesRefreshTheCurrentSearch() {
        query("设备");
        assertEquals(1, activity.getListAdapter().getCount());
        ContentValues change = new ContentValues();
        change.put("note", "水果");
        RuntimeEnvironment.getApplication().getContentResolver().update(
                NotePad.Notes.CONTENT_URI, change, "title = ?", new String[] { "购物" });
        drainQueries();
        assertEquals(0, activity.getListAdapter().getCount());
        assertEquals("设备", search().getQuery().toString());
    }
    @Test public void categoryFilterSurvivesClearingSearchAndRecreation() {
        ContentValues values = new ContentValues();
        values.put("category", "study");
        RuntimeEnvironment.getApplication().getContentResolver().update(NotePad.Notes.CONTENT_URI,
                values, "title = ?", new String[] { "安卓课程" });
        activity.findViewById(R.id.category_filters).findViewWithTag("study").performClick();
        query("安卓");
        assertEquals(1, activity.getListAdapter().getCount());
        assertEquals("安卓课程", titleAt(0));
        query("");
        assertEquals(1, activity.getListAdapter().getCount());
        controller.recreate();
        activity = controller.get();
        drainQueries();
        assertTrue(activity.findViewById(R.id.category_filters).findViewWithTag("study").isSelected());
        assertEquals(1, activity.getListAdapter().getCount());
        activity.findViewById(R.id.new_note).performClick();
        android.content.Intent intent = ((org.robolectric.shadows.ShadowActivity) org.robolectric.shadow.api.Shadow.extract(activity)).getNextStartedActivity();
        assertEquals("study", intent.getStringExtra("category"));
    }

    @Test public void emptyCategoryHasSpecificFeedbackAndPreviewBindsTheBody() {
        android.view.View row = activity.getListAdapter().getView(0, null, activity.getListView());
        assertEquals("安卓设备", ((TextView) row.findViewById(R.id.note_preview)).getText().toString());
        assertEquals("未分类", ((TextView) row.findViewById(R.id.note_category)).getText().toString());
        activity.findViewById(R.id.category_filters).findViewWithTag("work").performClick();
        drainQueries();
        assertEquals(0, activity.getListAdapter().getCount());
        assertEquals("这个分类还没有笔记", ((TextView) activity.findViewById(android.R.id.empty)).getText().toString());
    }

}
