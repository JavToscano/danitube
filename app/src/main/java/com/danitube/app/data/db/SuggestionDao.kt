package com.danitube.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SuggestionDao {
    @Query("SELECT * FROM suggestions WHERE status = 0 ORDER BY score DESC")
    fun observeStreamable(): Flow<List<SuggestionEntity>>

    @Query("SELECT * FROM suggestions WHERE status = 0 ORDER BY score DESC")
    suspend fun streamable(): List<SuggestionEntity>

    @Query("SELECT * FROM suggestions")
    suspend fun getAll(): List<SuggestionEntity>

    @Query("SELECT * FROM suggestions WHERE id = :id")
    suspend fun get(id: String): SuggestionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(suggestion: SuggestionEntity)

    @Query("UPDATE suggestions SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: Int): Int

    @Query("UPDATE suggestions SET playCount = playCount + 1, lastPlayedAt = :now WHERE id = :id")
    suspend fun recordPlay(id: String, now: Long)

    /** Drops suggestions nobody played that have been waiting since before [before]. */
    @Query("DELETE FROM suggestions WHERE status = 0 AND playCount = 0 AND addedAt < :before")
    suspend fun deleteStale(before: Long)
}
