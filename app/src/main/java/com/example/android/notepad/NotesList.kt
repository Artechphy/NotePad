/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package com.example.android.notepad

import android.annotation.TargetApi
import android.app.ListActivity
import android.app.LoaderManager
import android.app.UiModeManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.CursorLoader
import android.content.Intent
import android.content.Loader
import android.content.res.Configuration
import android.database.Cursor
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.SearchView
import android.widget.SimpleCursorAdapter
import android.widget.TextView
import java.util.Locale
import java.util.TimeZone

/**
 * Displays a list of notes. Will display notes from the [Uri]
 * provided in the incoming Intent if there is one, otherwise it defaults to displaying the
 * contents of the [NotePadProvider].
 *
 * List queries run in a CursorLoader; editor and context menu writes retain the sample's
 * existing behavior.
 */
class NotesList : ListActivity(), LoaderManager.LoaderCallbacks<Cursor> {

    private lateinit var mAdapter: SimpleCursorAdapter
    private lateinit var mEmptyView: TextView
    private var mQuery: String = ""
    private var mCategory: String? = null
    private lateinit var mCategoryFilters: LinearLayout

    /**
     * onCreate is called when Android starts this Activity from scratch.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.notes_list)
        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT)
        if (intent.data == null) {
            intent.data = NotePad.Notes.CONTENT_URI
        }
        mEmptyView = findViewById(android.R.id.empty)
        listView.setOnCreateContextMenuListener(this)
        mAdapter = SimpleCursorAdapter(
            this,
            R.layout.noteslist_item,
            null,
            arrayOf(
                NotePad.Notes.COLUMN_NAME_TITLE,
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
                NotePad.Notes.COLUMN_NAME_NOTE,
                NotePad.Notes.COLUMN_NAME_CATEGORY
            ),
            intArrayOf(
                android.R.id.text1,
                R.id.note_modified,
                R.id.note_preview,
                R.id.note_category
            ),
            0
        )
        mAdapter.viewBinder = SimpleCursorAdapter.ViewBinder { view, cursor, columnIndex ->
            when (view.id) {
                R.id.note_preview -> {
                    val preview = NoteText.preview(cursor.getString(columnIndex))
                    (view as TextView).text =
                        if (preview.isEmpty()) getString(R.string.empty_preview) else preview
                    true
                }
                R.id.note_category -> {
                    val categoryId = cursor.getString(columnIndex)
                    val badge = view as TextView
                    badge.text = NoteCategory.label(this@NotesList, categoryId)
                    badge.setBackgroundResource(NoteCategory.badgeBackground(categoryId))
                    badge.setTextColor(resources.getColor(NoteCategory.badgeTextColor(categoryId)))
                    true
                }
                android.R.id.text1 -> {
                    val title = cursor.getString(columnIndex)
                    (view as TextView).text =
                        if (title == null || title.trim { it <= ' ' }.isEmpty()) {
                            getString(R.string.untitled_note)
                        } else {
                            title
                        }
                    true
                }
                else -> {
                    val millis = if (cursor.isNull(columnIndex)) null else cursor.getLong(columnIndex)
                    val time = NoteTimestamp.format(millis, TimeZone.getDefault(), Locale.getDefault())
                    (view as TextView).text =
                        if (time == null) {
                            getString(R.string.time_unknown)
                        } else {
                            getString(R.string.modified_time, time)
                        }
                    true
                }
            }
        }
        listAdapter = mAdapter

        val search = findViewById<SearchView>(R.id.search_notes)
        search.isSaveFromParentEnabled = false
        if (savedInstanceState != null) {
            mQuery = NoteSearch.normalize(savedInstanceState.getString(SEARCH_QUERY))
            val category = savedInstanceState.getString("category")
            if (NoteCategory.indexOf(category) >= 0) mCategory = category
        }
        mCategoryFilters = findViewById(R.id.category_filters)
        addCategoryFilter(null, getString(R.string.category_all))
        val labels = NoteCategory.labels(this)
        for (i in labels.indices) addCategoryFilter(NoteCategory.idAt(i), labels[i])
        updateCategorySelection()
        findViewById<View>(R.id.new_note).setOnClickListener { createNote() }
        if (Build.VERSION.SDK_INT >= 31) {
            setupThemeToggle()
        }
        findViewById<View>(R.id.list_more).setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            menuInflater.inflate(R.menu.list_options_menu, popup.menu)
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
            popup.menu.findItem(R.id.menu_paste)?.isEnabled = clipboard?.hasPrimaryClip() == true
            popup.setOnMenuItemClickListener { item -> onOptionsItemSelected(item) }
            popup.show()
        }
        search.setQuery(mQuery, false)
        search.clearFocus()
        search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                updateSearch(query)
                search.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                updateSearch(newText)
                return true
            }
        })
        // Use a fresh loader after recreation so a restored keyword cannot reuse an old filter.
        loaderManager.restartLoader(NOTES_LOADER, null, this)
    }

    private fun updateSearch(text: String?) {
        val query = NoteSearch.normalize(text)
        if (query != mQuery) {
            mQuery = query
            // Do not leave old, clickable results visible for a different keyword.
            reloadNotes()
        }
    }

    private fun addCategoryFilter(category: String?, label: String) {
        val button = layoutInflater.inflate(R.layout.category_filter, mCategoryFilters, false) as Button
        button.text = label
        button.tag = category
        button.setOnClickListener {
            if (category == mCategory) return@setOnClickListener
            mCategory = category
            updateCategorySelection()
            reloadNotes()
        }
        mCategoryFilters.addView(button)
    }

    private fun updateCategorySelection() {
        for (i in 0 until mCategoryFilters.childCount) {
            val button = mCategoryFilters.getChildAt(i) as Button
            val selected = button.tag == mCategory
            button.isSelected = selected
            button.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun isNightMode(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun setupThemeToggle() {
        val toggle = findViewById<ImageButton>(R.id.theme_toggle) ?: return
        val night = isNightMode()
        toggle.visibility = View.VISIBLE
        toggle.setImageResource(if (night) R.drawable.ic_light_mode_24 else R.drawable.ic_dark_mode_24)
        toggle.contentDescription = getString(
            if (night) R.string.switch_theme_light else R.string.switch_theme_dark
        )
        toggle.setOnClickListener {
            NightModeToggle.toggle(this, !isNightMode())
        }
    }

    /** Keep API 31 platform calls out of legacy API paths. */
    @TargetApi(31)
    private object NightModeToggle {
        fun toggle(context: Context, enableNight: Boolean) {
            val manager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager ?: return
            manager.setApplicationNightMode(
                if (enableNight) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO
            )
        }
    }

    private fun reloadNotes() {
        mAdapter.swapCursor(null)
        mEmptyView.setText(R.string.notes_loading)
        findViewById<TextView>(R.id.note_count).setText(R.string.notes_loading)
        loaderManager.restartLoader(NOTES_LOADER, null, this)
    }

    private fun createNote() {
        val intent = Intent(Intent.ACTION_INSERT, notesUri()).setClass(this, NoteEditor::class.java)
        if (mCategory != null) intent.putExtra(NotePad.Notes.COLUMN_NAME_CATEGORY, mCategory)
        startActivity(intent)
    }

    private fun notesUri(): Uri = intent?.data ?: NotePad.Notes.CONTENT_URI

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(SEARCH_QUERY, mQuery)
        outState.putString("category", mCategory)
        super.onSaveInstanceState(outState)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        return NotesLoader(applicationContext, notesUri(), mQuery, mCategory)
    }

    /** Static (non-inner) so a retained loader never keeps the previous Activity alive. */
    private class NotesLoader(
        context: Context,
        uri: Uri,
        query: String,
        category: String?
    ) : CursorLoader(
        context,
        uri,
        PROJECTION,
        NoteSearch.selection(query, category),
        NoteSearch.arguments(query, category),
        "${NotePad.Notes.DEFAULT_SORT_ORDER}, ${NotePad.Notes._ID} DESC"
    ) {
        override fun loadInBackground(): Cursor? {
            return try {
                super.loadInBackground()
            } catch (error: RuntimeException) {
                Log.e(TAG, "Unable to load notes", error)
                null
            }
        }
    }

    override fun onLoadFinished(loader: Loader<Cursor>?, data: Cursor?) {
        mAdapter.swapCursor(data)
        mEmptyView.setText(
            when {
                data == null -> R.string.notes_load_error
                mQuery.isNotEmpty() -> R.string.search_empty
                mCategory != null -> R.string.category_empty
                else -> R.string.notes_empty
            }
        )
        findViewById<TextView>(R.id.note_count).text = getString(
            R.string.note_count,
            data?.count ?: 0
        )
    }

    override fun onLoaderReset(loader: Loader<Cursor>?) {
        // LoaderManager owns and closes cursors; the adapter only borrows them.
        mAdapter.swapCursor(null)
    }

    /**
     * Called when the user clicks the device's Menu button the first time for
     * this Activity. Android passes in a Menu object that is populated with items.
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate menu from XML resource
        menuInflater.inflate(R.menu.list_options_menu, menu)

        // Generate any additional actions that can be performed on the
        // overall list. In a normal install, there are no additional
        // actions found here, but this allows other applications to extend
        // our menu with their own actions.
        val intent = Intent(null, notesUri())
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE)
        menu.addIntentOptions(
            Menu.CATEGORY_ALTERNATIVE,
            0,
            0,
            ComponentName(this, NotesList::class.java),
            null,
            intent,
            0,
            null
        )

        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)

        // Android may prepare this menu during a return transition, before focus.
        // The visible popup checks again when clicked with an active window.
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
        menu.findItem(R.id.menu_paste)?.isEnabled =
            hasWindowFocus() && clipboard?.hasPrimaryClip() == true

        // Gets the number of notes currently being displayed.
        val haveItems = (listAdapter?.count ?: 0) > 0

        // If there are any notes in the list (which implies that one of
        // them is selected), then we need to generate the actions that
        // can be performed on the current selection.
        if (haveItems) {
            // This is the selected item.
            val uri = ContentUris.withAppendedId(notesUri(), selectedItemId)

            // Creates an array of Intents with one element. This will be used to send an Intent
            // based on the selected menu item.
            val specifics = arrayOf(Intent(Intent.ACTION_EDIT, uri).setClass(this, NoteEditor::class.java))

            // Creates an array of menu items with one element. This will contain the EDIT option.
            val items = arrayOfNulls<MenuItem>(1)

            // Creates an Intent with no specific action, using the URI of the selected note.
            val intent = Intent(null, uri)

            /* Adds the category ALTERNATIVE to the Intent, with the note ID URI as its
             * data. This prepares the Intent as a place to group alternative options in the
             * menu.
             */
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE)

            /*
             * Add alternatives to the menu
             */
            menu.addIntentOptions(
                Menu.CATEGORY_ALTERNATIVE,  // Add the Intents as options in the alternatives group.
                Menu.NONE,                  // A unique item ID is not required.
                Menu.NONE,                  // The alternatives don't need to be in order.
                null,                       // The caller's name is not excluded from the group.
                specifics,                  // These specific options must appear first.
                intent,                     // These Intent objects map to the options in specifics.
                Menu.NONE,                  // No flags are required.
                items                       // The menu items generated from the specifics-to-Intents mapping
            )
            // If the Edit menu item exists, adds shortcuts for it.
            items[0]?.setShortcut('1', 'e')
        } else {
            // If the list is empty, removes any existing alternative actions from the menu
            menu.removeGroup(Menu.CATEGORY_ALTERNATIVE)
        }

        // Displays the menu
        return true
    }

    /**
     * This method is called when the user selects an option from the menu, but no item
     * in the list is selected.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_add -> {
                createNote()
                true
            }
            R.id.menu_paste -> {
                startActivity(Intent(Intent.ACTION_PASTE, notesUri()).setClass(this, NoteEditor::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * This method is called when the user context-clicks a note in the list.
     */
    override fun onCreateContextMenu(menu: ContextMenu?, view: View?, menuInfo: ContextMenuInfo?) {
        // The data from the menu item.
        val info = try {
            // Casts the incoming data object into the type for AdapterView objects.
            menuInfo as AdapterView.AdapterContextMenuInfo
        } catch (e: ClassCastException) {
            // If the menu object can't be cast, logs an error.
            Log.e(TAG, "bad menuInfo", e)
            return
        }

        /*
         * Gets the data associated with the item at the selected position.
         */
        val cursor = listAdapter?.getItem(info.position) as? Cursor ?: return
        if (menu == null) return

        // Inflate menu from XML resource
        menuInflater.inflate(R.menu.list_context_menu, menu)

        // Sets the menu header to be the title of the selected note.
        menu.setHeaderTitle(cursor.getString(COLUMN_INDEX_TITLE))

        // Append menu items for any other activities that can do stuff with it as well.
        val intent = Intent(
            null,
            Uri.withAppendedPath(notesUri(), info.id.toInt().toString())
        )
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        menu.addIntentOptions(
            Menu.CATEGORY_ALTERNATIVE,
            0,
            0,
            ComponentName(this, NotesList::class.java),
            null,
            intent,
            0,
            null
        )
    }

    /**
     * This method is called when the user selects an item from the context menu.
     */
    override fun onContextItemSelected(item: MenuItem): Boolean {
        // The data from the menu item.
        val info = try {
            // Casts the data object in the item into the type for AdapterView objects.
            item.menuInfo as AdapterView.AdapterContextMenuInfo
        } catch (e: ClassCastException) {
            // If the object can't be cast, logs an error
            Log.e(TAG, "bad menuInfo", e)

            // Triggers default processing of the menu item.
            return false
        }

        // Appends the selected note's ID to the URI sent with the incoming Intent.
        val noteUri = ContentUris.withAppendedId(notesUri(), info.id)

        return when (item.itemId) {
            R.id.context_open -> {
                // Launch activity to view/edit the currently selected item
                startActivity(Intent(Intent.ACTION_EDIT, noteUri).setClass(this, NoteEditor::class.java))
                true
            }
            R.id.context_copy -> {
                // Gets a handle to the clipboard service.
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager

                // Copies the note's URI to the clipboard. In effect, this copies the note itself.
                clipboard.setPrimaryClip(
                    ClipData.newUri(
                        contentResolver,
                        "Note",
                        noteUri
                    )
                )
                true
            }
            R.id.context_delete -> {
                // Deletes the note from the provider by passing in a URI in note ID format.
                contentResolver.delete(noteUri, null, null)
                true
            }
            else -> super.onContextItemSelected(item)
        }
    }

    /**
     * This method is called when the user clicks a note in the displayed list.
     */
    override fun onListItemClick(l: ListView?, v: View?, position: Int, id: Long) {
        // Constructs a new URI from the incoming URI and the row ID
        val uri = ContentUris.withAppendedId(notesUri(), id)

        // Gets the action from the incoming Intent
        val action = intent.action

        // Handles requests for note data
        if (Intent.ACTION_PICK == action || Intent.ACTION_GET_CONTENT == action) {
            // Sets the result to return to the component that called this Activity.
            setResult(RESULT_OK, Intent().setData(uri))
        } else {
            // Sends out an Intent to start an Activity that can handle ACTION_EDIT.
            startActivity(Intent(Intent.ACTION_EDIT, uri).setClass(this, NoteEditor::class.java))
        }
    }

    companion object {
        private const val NOTES_LOADER = 0
        private const val SEARCH_QUERY = "search_query"

        // For logging and debugging
        private const val TAG = "NotesList"

        /**
         * The columns needed by the cursor adapter
         */
        private val PROJECTION = arrayOf(
            NotePad.Notes._ID, // 0
            NotePad.Notes.COLUMN_NAME_TITLE, // 1
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, // 2
            NotePad.Notes.COLUMN_NAME_NOTE, // 3
            NotePad.Notes.COLUMN_NAME_CATEGORY // 4
        )

        /** The index of the title column */
        private const val COLUMN_INDEX_TITLE = 1
    }
}
