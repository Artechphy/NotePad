package com.example.android.notepad;

import android.app.Activity;
import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

/** Native Android View renders with fixture data, not screenshots from a running device. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w390dp-h844dp-port-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.LEGACY)
public class NoteUiPreviewTest {
    private Uri insert(String title, String body, String category, long modified) {
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("note", body);
        values.put("category", category);
        values.put("modified", modified);
        return RuntimeEnvironment.getApplication().getContentResolver().insert(NotePad.Notes.CONTENT_URI, values);
    }

    private void render(Activity activity, String name) throws Exception {
        View decor = activity.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(780, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1688, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, 780, 1688);
        Bitmap bitmap = Bitmap.createBitmap(780, 1688, Bitmap.Config.ARGB_8888);
        decor.draw(new Canvas(bitmap));
        File directory = new File("build/outputs/ui-preview");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
        bitmap.recycle();
    }

    @Test public void rendersListAndEditorAtPhoneSize() throws Exception {
        Robolectric.getBackgroundThreadScheduler().pause();
        Robolectric.getForegroundThreadScheduler().pause();
        Uri note = insert("把想法写下来", "让记录回归简单。\n\n灵感、课程重点，还有生活里的小事，都值得留在这里。\n\n今天的计划\n1. 完成安卓实验\n2. 整理课堂笔记\n3. 留一点时间阅读", "life", 1794057000000L);
        insert("Android 课堂笔记", "Activity 的生命周期：创建、启动、恢复、暂停。用一个小实验理解页面切换时发生的事。", "study", 1794053400000L);
        insert("本周的小目标", "读完一本书，整理桌面，试着把每一天过得更有条理。", "uncategorized", 1794049800000L);
        ActivityController<NotesList> list = Robolectric.buildActivity(NotesList.class).setup().visible();
        for (int i = 0; i < 3; i++) {
            Robolectric.flushForegroundThreadScheduler();
            Robolectric.flushBackgroundThreadScheduler();
        }
        Robolectric.flushForegroundThreadScheduler();
        assertEquals(3, list.get().getListAdapter().getCount());
        render(list.get(), "notes-list");
        list.pause().stop().destroy();
        ActivityController<NoteEditor> editor = Robolectric.buildActivity(NoteEditor.class,
                new Intent(Intent.ACTION_EDIT, note)).setup().visible();
        render(editor.get(), "note-editor");
        editor.pause().stop().destroy();
        ContentProviderClient client = RuntimeEnvironment.getApplication().getContentResolver()
                .acquireContentProviderClient(NotePad.Notes.CONTENT_URI);
        ((NotePadProvider) client.getLocalContentProvider()).getOpenHelperForTest().close();
        client.release();
    }
}
