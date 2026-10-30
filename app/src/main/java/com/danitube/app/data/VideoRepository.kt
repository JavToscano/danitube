package com.danitube.app.data

import com.danitube.app.data.db.SuggestionDao
import com.danitube.app.data.db.SuggestionEntity
import com.danitube.app.data.db.VideoDao
import com.danitube.app.data.db.VideoEntity
import com.danitube.app.data.download.VideoStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepository @Inject constructor(
    private val dao: VideoDao,
    private val suggestions: SuggestionDao,
    private val storage: VideoStorage,
    private val backup: BackupManager,
) {
    val videos: Flow<List<VideoEntity>> = dao.observeAll()

    suspend fun add(videos: List<VideoEntity>) = dao.upsert(videos)

    /**
     * Adds freshly downloaded videos. One that was a suggestion is no longer streamed, and keeps the
     * plays it already had.
     */
    suspend fun addDownloaded(videos: List<VideoEntity>) {
        val merged = videos.map { video ->
            val suggestion = suggestions.get(video.id) ?: return@map video
            suggestions.setStatus(video.id, SuggestionEntity.STATUS_DOWNLOADED)
            video.copy(playCount = suggestion.playCount, lastPlayedAt = suggestion.lastPlayedAt)
        }
        dao.upsert(merged)
    }

    suspend fun all(): List<VideoEntity> = dao.getAll()

    /** Counts a play for a library video or a streamed suggestion (only one table has the id). */
    suspend fun recordPlay(id: String) {
        val now = System.currentTimeMillis()
        dao.recordPlay(id, now)
        suggestions.recordPlay(id, now)
    }

    /** Deletes a library video or removes a streamed suggestion; either way it is never suggested again. */
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val video = dao.get(id)
        video?.let { v ->
            storage.delete(v.videoFile)
            v.thumbFile?.let(storage::delete)
        }
        dao.delete(id)
        if (suggestions.setStatus(id, SuggestionEntity.STATUS_REJECTED) == 0) {
            suggestions.upsert(
                SuggestionEntity(
                    id = id, title = video?.title.orEmpty(), channel = null, durationSec = 0, tags = "",
                    score = 0f, addedAt = System.currentTimeMillis(), status = SuggestionEntity.STATUS_REJECTED,
                ),
            )
        }
        runCatching { backup.sync(removedId = id) }
    }
}
