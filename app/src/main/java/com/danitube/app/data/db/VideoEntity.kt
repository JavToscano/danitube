package com.danitube.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A downloaded video. Paths are relative to [com.danitube.app.data.download.VideoStorage.dir]. */
@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val id: String,
    val title: String,
    val durationSec: Int,
    val videoFile: String,
    val thumbFile: String?,
    val addedAt: Long,
    @ColumnInfo(defaultValue = "0") val playCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val lastPlayedAt: Long = 0,
    /** YouTube channel; null until known (videos downloaded before suggestions existed). */
    val channel: String? = null,
    /** Tags joined by [TAG_SEPARATOR]; null until fetched, "" when the video has none. */
    val tags: String? = null,
    /** A vertical video of up to 3 minutes, like YouTube's Shorts; null until its size is known. */
    val isShort: Boolean? = null,
) {
    val tagList: List<String> get() = tags.orEmpty().split(TAG_SEPARATOR).filter { it.isNotBlank() }

    companion object {
        const val SHORT_MAX_SEC = 180

        /** Vertical and short enough to be a Short; null when the picture size is unknown. */
        fun shapeIsShort(width: Int, height: Int, durationSec: Int): Boolean? =
            if (width <= 0 || height <= 0) null else height > width && durationSec <= SHORT_MAX_SEC

        const val TAG_SEPARATOR = "\n"
        fun joinTags(tags: List<String>) = tags.joinToString(TAG_SEPARATOR)
    }
}
