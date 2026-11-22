@file:Suppress("DEPRECATION")

package com.example.android.notepad

import android.content.ContentResolver
import android.net.Uri
import android.os.AsyncTask
import android.util.Log
import java.io.IOException
import java.lang.ref.WeakReference

/** A local document write; retained across rotation, with no strong Activity reference. */
internal class NoteExportTask(
    private val resolver: ContentResolver,
    private val document: String,
) : AsyncTask<Uri?, Void?, Exception?>() {

    private var editor: WeakReference<NoteEditor?> = WeakReference(null)
    private var finished: Boolean = false
    private var delivered: Boolean = false
    private var failure: Exception? = null

    internal fun attach(activity: NoteEditor) {
        editor = WeakReference(activity)
        if (finished) {
            deliver()
        } else {
            activity.showExportProgress()
        }
    }

    internal fun detach() {
        editor.clear()
    }

    @Deprecated("Deprecated in Java")
    override fun doInBackground(vararg uris: Uri?): Exception? {
        return try {
            val uri = uris.firstOrNull() ?: throw IOException("Missing export document URI")
            NoteExport.write(resolver.openOutputStream(uri, "wt"), document)
            null
        } catch (error: Exception) {
            Log.e("NoteExport", "Unable to write document", error)
            error
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onPostExecute(error: Exception?) {
        failure = error
        finished = true
        deliver()
    }

    private fun deliver() {
        val activity = editor.get()
        if (!delivered && activity != null && !activity.isFinishing) {
            delivered = true
            activity.showExportResult(failure == null)
        }
    }
}
