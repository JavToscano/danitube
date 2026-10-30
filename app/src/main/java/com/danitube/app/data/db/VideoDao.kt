package com.danitube.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE id = :id")
    suspend fun get(id: String): VideoEntity?

    @Query("SELECT * FROM videos")
    suspend fun getAll(): List<VideoEntity>

    /** Videos whose tags were never fetched (downloaded before suggestions existed). */
    @Query("SELECT * FROM videos WHERE tags IS NULL LIMIT :limit")
    suspend fun withoutTags(limit: Int): List<VideoEntity>

    @Query("UPDATE videos SET channel = :channel, tags = :tags WHERE id = :id")
    suspend fun setMeta(id: String, channel: String?, tags: String)

    @Query("UPDATE videos SET playCount = playCount + 1, lastPlayedAt = :now WHERE id = :id")
    suspend fun recordPlay(id: String, now: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(videos: List<VideoEntity>)

    @Query("DELETE FROM videos WHERE id = :id")
    suspend fun delete(id: String)
}
