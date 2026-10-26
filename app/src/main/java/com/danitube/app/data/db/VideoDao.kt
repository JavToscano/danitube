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

    @Query("UPDATE videos SET playCount = playCount + 1, lastPlayedAt = :now WHERE id = :id")
    suspend fun recordPlay(id: String, now: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(videos: List<VideoEntity>)

    @Query("DELETE FROM videos WHERE id = :id")
    suspend fun delete(id: String)
}
