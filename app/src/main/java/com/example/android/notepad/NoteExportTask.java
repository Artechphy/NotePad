package com.example.android.notepad;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import java.lang.ref.WeakReference;

/** A local document write; retained across rotation, with no strong Activity reference. */
final class NoteExportTask extends AsyncTask<Uri, Void, Exception> {
    private final ContentResolver resolver;
    private final String document;
    private WeakReference<NoteEditor> editor = new WeakReference<>(null);
    private boolean finished;
    private boolean delivered;
    private Exception failure;

    NoteExportTask(ContentResolver resolver, String document) {
        this.resolver = resolver;
        this.document = document;
    }

    void attach(NoteEditor activity) {
        editor = new WeakReference<>(activity);
        if (finished) deliver();
        else activity.showExportProgress();
    }

    void detach() {
        editor.clear();
    }

    @Override protected Exception doInBackground(Uri... uris) {
        try {
            NoteExport.write(resolver.openOutputStream(uris[0], "wt"), document);
            return null;
        } catch (Exception error) {
            Log.e("NoteExport", "Unable to write document", error);
            return error;
        }
    }

    @Override protected void onPostExecute(Exception error) {
        failure = error;
        finished = true;
        deliver();
    }

    private void deliver() {
        NoteEditor activity = editor.get();
        if (!delivered && activity != null && !activity.isFinishing()) {
            delivered = true;
            activity.showExportResult(failure == null);
        }
    }
}
