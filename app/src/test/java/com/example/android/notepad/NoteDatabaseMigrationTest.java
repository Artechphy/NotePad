package com.example.android.notepad;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class NoteDatabaseMigrationTest {
    private void createOldDatabase(int version) {
        Context context = RuntimeEnvironment.getApplication();
        try (SQLiteDatabase db = context.openOrCreateDatabase("note_pad.db", 0, null)) {
            db.execSQL("CREATE TABLE notes (_id INTEGER PRIMARY KEY, title TEXT, note TEXT, created INTEGER, modified INTEGER)");
            db.execSQL("INSERT INTO notes VALUES (?, ?, ?, ?, ?)",
                    new Object[] { 47, "旧标题 O'Reilly", "中文旧正文", 1234L, 5678L });
            db.setVersion(version);
        }
    }

    @Test public void realVersionTwoFileUpgradesInPlaceAndPreservesEveryOldField() {
        createOldDatabase(2);
        NotePadProvider.DatabaseHelper helper = new NotePadProvider.DatabaseHelper(RuntimeEnvironment.getApplication());
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            assertEquals(3, db.getVersion());
            try (Cursor row = db.rawQuery("SELECT _id, title, note, created, modified, category FROM notes", null)) {
                assertEquals(1, row.getCount());
                assertTrue(row.moveToFirst());
                assertEquals(47L, row.getLong(0));
                assertEquals("旧标题 O'Reilly", row.getString(1));
                assertEquals("中文旧正文", row.getString(2));
                assertEquals(1234L, row.getLong(3));
                assertEquals(5678L, row.getLong(4));
                assertEquals("uncategorized", row.getString(5));
            }
            helper.close();
            try (Cursor row = helper.getReadableDatabase().rawQuery("SELECT count(*) FROM notes", null)) {
                assertTrue(row.moveToFirst());
                assertEquals(1, row.getInt(0));
            }
        } finally { helper.close(); }
    }

    @Test public void unknownUpgradeFailsWithoutDestroyingTheOldFile() {
        createOldDatabase(1);
        Context context = RuntimeEnvironment.getApplication();
        NotePadProvider.DatabaseHelper helper = new NotePadProvider.DatabaseHelper(context);
        try {
            assertThrows(android.database.SQLException.class, helper::getWritableDatabase);
        } finally { helper.close(); }
        try (SQLiteDatabase db = context.openOrCreateDatabase("note_pad.db", 0, null);
             Cursor row = db.rawQuery("SELECT _id, note FROM notes", null)) {
            assertEquals(1, db.getVersion());
            assertTrue(row.moveToFirst());
            assertEquals(47L, row.getLong(0));
            assertEquals("中文旧正文", row.getString(1));
        }
    }
}
