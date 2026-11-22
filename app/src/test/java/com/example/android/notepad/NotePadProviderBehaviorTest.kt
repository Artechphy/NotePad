@file:Suppress("DEPRECATION")

package com.example.android.notepad

import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.net.Uri
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Component tests use only Robolectric's isolated application database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotePadProviderBehaviorTest {
    private lateinit var provider: NotePadProvider

    @Before
    fun setUp() {
        provider = NotePadProvider()
        val info = ProviderInfo()
        info.authority = NotePad.AUTHORITY
        provider.attachInfo(RuntimeEnvironment.getApplication(), info)
    }

    @After
    fun tearDown() {
        provider.getOpenHelperForTest().close()
    }

    private fun insert(title: String, body: String): Uri {
        val values = ContentValues().apply {
            put("title", title)
            put("note", body)
            put("created", 1000L)
            put("modified", 2000L)
        }
        return provider.insert(NotePad.Notes.CONTENT_URI, values)
    }

    private fun search(keyword: String): List<String> {
        val titles = ArrayList<String>()
        provider.query(
            NotePad.Notes.CONTENT_URI,
            arrayOf("title"),
            NoteSearch.selection(keyword),
            NoteSearch.arguments(keyword),
            "_id ASC"
        )!!.use { cursor ->
            while (cursor.moveToNext()) titles.add(cursor.getString(0))
        }
        return titles
    }

    private fun time(uri: Uri, column: String): Long {
        provider.query(uri, arrayOf(column), null, null, null)!!.use { cursor ->
            assertTrue(cursor.moveToFirst())
            return cursor.getLong(0)
        }
    }

    @Test
    fun titleOrBodyMatchesChineseAndClearingRestoresAll() {
        insert("安卓课程", "界面实验")
        insert("购物", "安卓设备")
        insert("周末", "阅读")
        assertEquals(listOf("安卓课程", "购物"), search(" 安卓 "))
        assertEquals(listOf("安卓课程", "购物", "周末"), search(" "))
        assertTrue(search("不存在").isEmpty())
    }

    @Test
    fun specialCharactersMatchLiterallyInRealSqlite() {
        insert("折扣50%", "")
        insert("变量_a", "")
        insert("惊叹!", "")
        insert("目录\\a", "")
        insert("书籍", "O'Reilly")
        insert("普通", "text")
        assertEquals(listOf("折扣50%"), search("%"))
        assertEquals(listOf("变量_a"), search("_"))
        assertEquals(listOf("惊叹!"), search("!"))
        assertEquals(listOf("目录\\a"), search("\\"))
        assertEquals(listOf("书籍"), search("O'Reilly"))
        assertTrue(search("' OR 1=1 --").isEmpty())
    }

    @Test
    fun insertInitializesBothTimestamps() {
        val before = System.currentTimeMillis()
        val uri = provider.insert(NotePad.Notes.CONTENT_URI, ContentValues())!!
        val after = System.currentTimeMillis()
        val created = time(uri, "created")
        assertTrue(created in before..after)
        assertEquals(created, time(uri, "modified"))
    }

    @Test
    fun changingOnlyTitleUpdatesModifiedAndPreservesCreated() {
        val uri = insert("旧标题", "正文")
        val change = ContentValues().apply { put("title", "新标题") }
        val before = System.currentTimeMillis()
        assertEquals(1, provider.update(uri, change, null, null))
        assertTrue(time(uri, "modified") >= before)
        assertEquals(1000L, time(uri, "created"))
        assertFalse(change.containsKey("modified")) // Do not mutate the caller's map.
    }

    @Test
    fun viewingSearchingAndUnchangedSavingPreserveTimestamp() {
        val uri = insert("标题", "正文")
        search("正文")
        time(uri, "modified")
        val unchanged = ContentValues().apply {
            put("title", "标题")
            put("note", "正文")
        }
        assertEquals(0, provider.update(uri, unchanged, null, null))
        assertEquals(2000L, time(uri, "modified"))
    }

    @Test
    fun bodyChangesAreSearchableAndDoNotAffectOtherNotes() {
        val edited = insert("标题", "旧正文")
        val other = insert("其他", "正文")
        val change = ContentValues().apply { put("note", "新增关键词") }
        assertEquals(1, provider.update(edited, change, null, null))
        assertEquals(listOf("标题"), search("新增关键词"))
        assertTrue(time(edited, "modified") > 2000L)
        assertEquals(2000L, time(other, "modified"))
    }

    @Test
    fun bulkUpdateOnlyTouchesChangedRowsMatchingSelection() {
        val same = insert("保持", "目标")
        val changed = insert("替换", "目标")
        val excluded = insert("替换", "排除")
        val change = ContentValues().apply { put("title", "保持") }
        assertEquals(
            1,
            provider.update(NotePad.Notes.CONTENT_URI, change, "note = ?", arrayOf("目标"))
        )
        assertEquals(2000L, time(same, "modified"))
        assertTrue(time(changed, "modified") > 2000L)
        assertEquals(2000L, time(excluded, "modified"))
    }

    @Test
    fun idSelectionCannotLeakThroughOrCondition() {
        val first = insert("第一", "a")
        val second = insert("第二", "b")
        val change = ContentValues().apply { put("title", "更新") }
        assertEquals(
            1,
            provider.update(first, change, "note = ? OR note = ?", arrayOf("a", "b"))
        )
        assertEquals(listOf("更新", "第二"), search(""))
        assertEquals(2000L, time(second, "modified"))
    }

    @Test
    fun explicitHistoricalTimestampsRemainSupported() {
        val uri = insert("标题", "正文")
        val change = ContentValues().apply {
            put("note", "导入正文")
            put("modified", 12345L)
        }
        assertEquals(1, provider.update(uri, change, null, null))
        assertEquals(12345L, time(uri, "modified"))
    }

    @Test
    fun versionThreeDataSurvivesReopening() {
        val uri = insert("已有笔记", "旧内容")
        assertEquals(3, provider.getOpenHelperForTest().readableDatabase.version)
        provider.getOpenHelperForTest().close()
        assertEquals(listOf("已有笔记"), search("旧内容"))
        assertEquals(1000L, time(uri, "created"))
        assertEquals(2000L, time(uri, "modified"))
    }

    @Test
    fun categoryCombinesWithBothSidesOfTheSearchOr() {
        val study = insert("安卓课程", "学习笔记")
        val life = insert("安卓购物", "其他内容")
        val bodyMatch = insert("复习计划", "安卓实验")
        val values = ContentValues().apply { put("category", "study") }
        provider.update(study, values, null, null)
        provider.update(bodyMatch, values, null, null)
        values.put("category", "life")
        provider.update(life, values, null, null)
        provider.query(
            NotePad.Notes.CONTENT_URI,
            arrayOf("title"),
            NoteSearch.selection("安卓", "study"),
            NoteSearch.arguments("安卓", "study"),
            "_id ASC"
        )!!.use { rows ->
            assertEquals(2, rows.count)
            assertTrue(rows.moveToFirst())
            assertEquals("安卓课程", rows.getString(0))
            assertTrue(rows.moveToNext())
            assertEquals("复习计划", rows.getString(0))
        }
        provider.query(
            NotePad.Notes.CONTENT_URI,
            arrayOf("title"),
            NoteSearch.selection("", "life"),
            NoteSearch.arguments("", "life"),
            null
        )!!.use { rows ->
            assertEquals(1, rows.count)
        }
    }

    @Test
    fun categoryChangesPersistAndInvalidCategoriesCannotModifyData() {
        val uri = insert("标题", "正文")
        val values = ContentValues().apply { put("category", "work") }
        provider.update(uri, values, null, null)
        assertTrue(time(uri, "modified") > 2000L)
        provider.getOpenHelperForTest().close()
        provider.query(uri, arrayOf("category"), null, null, null)!!.use { row ->
            assertTrue(row.moveToFirst())
            assertEquals("work", row.getString(0))
        }
        values.put("category", "unknown")
        assertThrows(IllegalArgumentException::class.java) { provider.update(uri, values, null, null) }
        assertThrows(IllegalArgumentException::class.java) {
            provider.insert(NotePad.Notes.CONTENT_URI, values)
        }
        values.putNull("category")
        assertThrows(IllegalArgumentException::class.java) { provider.update(uri, values, null, null) }
        provider.query(uri, arrayOf("category"), null, null, null)!!.use { row ->
            assertTrue(row.moveToFirst())
            assertEquals("work", row.getString(0))
        }
    }
}
