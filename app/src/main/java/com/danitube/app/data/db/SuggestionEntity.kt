package com.danitube.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A YouTube video found because it is like what she watches. While [STATUS_SUGGESTED] it can be
 * streamed (on Wi-Fi); it becomes [STATUS_DOWNLOADED] once it is in the library, or
 * [STATUS_REJECTED] when a parent deletes it, so it is never suggested again.
 */
@Entity(tableName = "suggestions")
data class SuggestionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val channel: String?,
    val durationSec: Int,
    /** Tags joined by [VideoEntity.TAG_SEPARATOR]. */
    val tags: String,
    val score: Float,
    val addedAt: Long,
    val status: Int,
    @ColumnInfo(defaultValue = "0") val playCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val lastPlayedAt: Long = 0,
) {
    val tagList: List<String> get() = tags.split(VideoEntity.TAG_SEPARATOR).filter { it.isNotBlank() }

    companion object {
        const val STATUS_SUGGESTED = 0
        const val STATUS_DOWNLOADED = 1
        const val STATUS_REJECTED = 2
    }
}
