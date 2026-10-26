package com.danitube.app.data.download

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** App-private folder holding videos and thumbnails; nothing here is visible to other apps. */
@Singleton
class VideoStorage @Inject constructor(@ApplicationContext context: Context) {
    val dir: File = File(context.filesDir, "videos").apply { mkdirs() }

    fun file(name: String): File = File(dir, name)

    fun usedBytes(): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun freeBytes(): Long = dir.usableSpace
}
