@file:Suppress("DEPRECATION")

package com.example.android.notepad

import android.database.SQLException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteDatabaseMigrationTest {
    private fun createOldDatabase(version: Int) {
        val context = RuntimeEnvironment.getApplication()
        context.openOrCreateDatabase("note_pad.db", 0, null).use { db ->
            db.execSQL("CREATE TABLE notes (_id INTEGER PRIMARY KEY, title TEXT, note TEXT, created INTEGER, modified INTEGER)")
            db.execSQL(
                "INSERT INTO notes VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any>(47, "旧标题 O'Reilly", "中文旧正文", 1234L, 5678L)
            )
            db.version = version
        }
    }

    @Test
    fun realVersionTwoFileUpgradesInPlaceAndPreservesEveryOldField() {
        createOldDatabase(2)
        val helper = NotePadProvider.DatabaseHelper(RuntimeEnvironment.getApplication())
        try {
            val db = helper.writableDatabase
            assertEquals(3, db.version)
            db.rawQuery("SELECT _id, title, note, created, modified, category FROM notes", null).use { row ->
                assertEquals(1, row.count)
                assertTrue(row.moveToFirst())
                assertEquals(47L, row.getLong(0))
                assertEquals("旧标题 O'Reilly", row.getString(1))
                assertEquals("中文旧正文", row.getString(2))
                assertEquals(1234L, row.getLong(3))
                assertEquals(5678L, row.getLong(4))
                assertEquals("uncategorized", row.getString(5))
            }
            helper.close()
            helper.readableDatabase.rawQuery("SELECT count(*) FROM notes", null).use { row ->
                assertTrue(row.moveToFirst())
                assertEquals(1, row.getInt(0))
            }
        } finally {
            helper.close()
        }
    }

    @Test
    fun unknownUpgradeFailsWithoutDestroyingTheOldFile() {
        createOldDatabase(1)
        val context = RuntimeEnvironment.getApplication()
        val helper = NotePadProvider.DatabaseHelper(context)
        try {
            assertThrows(SQLException::class.java) { helper.writableDatabase }
        } finally {
            helper.close()
        }
        context.openOrCreateDatabase("note_pad.db", 0, null).use { db ->
            db.rawQuery("SELECT _id, note FROM notes", null).use { row ->
                assertEquals(1, db.version)
                assertTrue(row.moveToFirst())
                assertEquals(47L, row.getLong(0))
                assertEquals("中文旧正文", row.getString(1))
            }
        }
    }
}
