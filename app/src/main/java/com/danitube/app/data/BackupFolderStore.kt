package com.danitube.app.data

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Remembers the folder the parent picked for the library copy; unset until they pick one. */
@Singleton
class BackupFolderStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("backup", Context.MODE_PRIVATE)

    var tree: Uri?
        get() = prefs.getString(KEY_TREE, null)?.let(Uri::parse)
        set(value) = prefs.edit { putString(KEY_TREE, value?.toString()) }

    private companion object {
        const val KEY_TREE = "tree"
    }
}
