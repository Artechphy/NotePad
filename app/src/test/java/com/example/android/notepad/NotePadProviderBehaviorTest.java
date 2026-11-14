package com.example.android.notepad;

import android.content.ContentValues;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

/** Component tests use only Robolectric's isolated application database. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class NotePadProviderBehaviorTest {
    private NotePadProvider provider;

    @Before public void setUp() {
        provider = new NotePadProvider();
        ProviderInfo info = new ProviderInfo();
        info.authority = NotePad.AUTHORITY;
        provider.attachInfo(RuntimeEnvironment.getApplication(), info);
    }

    @After public void tearDown() {
        provider.getOpenHelperForTest().close();
    }

    private Uri insert(String title, String body) {
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("note", body);
        values.put("created", 1000L);
        values.put("modified", 2000L);
        return provider.insert(NotePad.Notes.CONTENT_URI, values);
    }

    private List<String> search(String keyword) {
        ArrayList<String> titles = new ArrayList<>();
        try (Cursor cursor = provider.query(NotePad.Notes.CONTENT_URI,
                new String[] { "title" }, NoteSearch.selection(keyword),
                NoteSearch.arguments(keyword), "_id ASC")) {
            while (cursor.moveToNext()) titles.add(cursor.getString(0));
        }
        return titles;
    }

    private long time(Uri uri, String column) {
        try (Cursor cursor = provider.query(uri, new String[] { column }, null, null, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getLong(0);
        }
    }

    @Test public void titleOrBodyMatchesChineseAndClearingRestoresAll() {
        insert("安卓课程", "界面实验");
        insert("购物", "安卓设备");
        insert("周末", "阅读");
        assertEquals(Arrays.asList("安卓课程", "购物"), search(" 安卓 "));
        assertEquals(Arrays.asList("安卓课程", "购物", "周末"), search(" "));
        assertTrue(search("不存在").isEmpty());
    }

    @Test public void specialCharactersMatchLiterallyInRealSqlite() {
        insert("折扣50%", "");
        insert("变量_a", "");
        insert("惊叹!", "");
        insert("目录\\a", "");
        insert("书籍", "O'Reilly");
        insert("普通", "text");
        assertEquals(Arrays.asList("折扣50%"), search("%"));
        assertEquals(Arrays.asList("变量_a"), search("_"));
        assertEquals(Arrays.asList("惊叹!"), search("!"));
        assertEquals(Arrays.asList("目录\\a"), search("\\"));
        assertEquals(Arrays.asList("书籍"), search("O'Reilly"));
        assertTrue(search("' OR 1=1 --").isEmpty());
    }

    @Test public void insertInitializesBothTimestamps() {
        long before = System.currentTimeMillis();
        Uri uri = provider.insert(NotePad.Notes.CONTENT_URI, new ContentValues());
        long after = System.currentTimeMillis();
        long created = time(uri, "created");
        assertTrue(created >= before && created <= after);
        assertEquals(created, time(uri, "modified"));
    }

    @Test public void changingOnlyTitleUpdatesModifiedAndPreservesCreated() {
        Uri uri = insert("旧标题", "正文");
        ContentValues change = new ContentValues();
        change.put("title", "新标题");
        long before = System.currentTimeMillis();
        assertEquals(1, provider.update(uri, change, null, null));
        assertTrue(time(uri, "modified") >= before);
        assertEquals(1000L, time(uri, "created"));
        assertFalse(change.containsKey("modified")); // Do not mutate the caller's map.
    }

    @Test public void viewingSearchingAndUnchangedSavingPreserveTimestamp() {
        Uri uri = insert("标题", "正文");
        search("正文");
        time(uri, "modified");
        ContentValues unchanged = new ContentValues();
        unchanged.put("title", "标题");
        unchanged.put("note", "正文");
        assertEquals(0, provider.update(uri, unchanged, null, null));
        assertEquals(2000L, time(uri, "modified"));
    }

    @Test public void bodyChangesAreSearchableAndDoNotAffectOtherNotes() {
        Uri edited = insert("标题", "旧正文");
        Uri other = insert("其他", "正文");
        ContentValues change = new ContentValues();
        change.put("note", "新增关键词");
        assertEquals(1, provider.update(edited, change, null, null));
        assertEquals(Arrays.asList("标题"), search("新增关键词"));
        assertTrue(time(edited, "modified") > 2000L);
        assertEquals(2000L, time(other, "modified"));
    }

    @Test public void bulkUpdateOnlyTouchesChangedRowsMatchingSelection() {
        Uri same = insert("保持", "目标");
        Uri changed = insert("替换", "目标");
        Uri excluded = insert("替换", "排除");
        ContentValues change = new ContentValues();
        change.put("title", "保持");
        assertEquals(1, provider.update(NotePad.Notes.CONTENT_URI, change,
                "note = ?", new String[] { "目标" }));
        assertEquals(2000L, time(same, "modified"));
        assertTrue(time(changed, "modified") > 2000L);
        assertEquals(2000L, time(excluded, "modified"));
    }

    @Test public void idSelectionCannotLeakThroughOrCondition() {
        Uri first = insert("第一", "a");
        Uri second = insert("第二", "b");
        ContentValues change = new ContentValues();
        change.put("title", "更新");
        assertEquals(1, provider.update(first, change, "note = ? OR note = ?",
                new String[] { "a", "b" }));
        assertEquals(Arrays.asList("更新", "第二"), search(""));
        assertEquals(2000L, time(second, "modified"));
    }

    @Test public void explicitHistoricalTimestampsRemainSupported() {
        Uri uri = insert("标题", "正文");
        ContentValues change = new ContentValues();
        change.put("note", "导入正文");
        change.put("modified", 12345L);
        assertEquals(1, provider.update(uri, change, null, null));
        assertEquals(12345L, time(uri, "modified"));
    }

    @Test public void versionThreeDataSurvivesReopening() {
        Uri uri = insert("已有笔记", "旧内容");
        assertEquals(3, provider.getOpenHelperForTest().getReadableDatabase().getVersion());
        provider.getOpenHelperForTest().close();
        assertEquals(Arrays.asList("已有笔记"), search("旧内容"));
        assertEquals(1000L, time(uri, "created"));
        assertEquals(2000L, time(uri, "modified"));
    }
    @Test public void categoryCombinesWithBothSidesOfTheSearchOr() {
        Uri study = insert("安卓课程", "学习笔记");
        Uri life = insert("安卓购物", "其他内容");
        Uri bodyMatch = insert("复习计划", "安卓实验");
        ContentValues values = new ContentValues();
        values.put("category", "study");
        provider.update(study, values, null, null);
        provider.update(bodyMatch, values, null, null);
        values.put("category", "life");
        provider.update(life, values, null, null);
        try (Cursor rows = provider.query(NotePad.Notes.CONTENT_URI, new String[] { "title" },
                NoteSearch.selection("安卓", "study"), NoteSearch.arguments("安卓", "study"), "_id ASC")) {
            assertEquals(2, rows.getCount());
            assertTrue(rows.moveToFirst());
            assertEquals("安卓课程", rows.getString(0));
            assertTrue(rows.moveToNext());
            assertEquals("复习计划", rows.getString(0));
        }
        try (Cursor rows = provider.query(NotePad.Notes.CONTENT_URI, new String[] { "title" },
                NoteSearch.selection("", "life"), NoteSearch.arguments("", "life"), null)) {
            assertEquals(1, rows.getCount());
        }
    }

    @Test public void categoryChangesPersistAndInvalidCategoriesCannotModifyData() {
        Uri uri = insert("标题", "正文");
        ContentValues values = new ContentValues();
        values.put("category", "work");
        provider.update(uri, values, null, null);
        assertTrue(time(uri, "modified") > 2000L);
        provider.getOpenHelperForTest().close();
        try (Cursor row = provider.query(uri, new String[] { "category" }, null, null, null)) {
            assertTrue(row.moveToFirst());
            assertEquals("work", row.getString(0));
        }
        values.put("category", "unknown");
        assertThrows(IllegalArgumentException.class, () -> provider.update(uri, values, null, null));
        assertThrows(IllegalArgumentException.class, () -> provider.insert(NotePad.Notes.CONTENT_URI, values));
        values.putNull("category");
        assertThrows(IllegalArgumentException.class, () -> provider.update(uri, values, null, null));
        try (Cursor row = provider.query(uri, new String[] { "category" }, null, null, null)) {
            assertTrue(row.moveToFirst());
            assertEquals("work", row.getString(0));
        }
    }

}
