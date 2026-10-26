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
)
