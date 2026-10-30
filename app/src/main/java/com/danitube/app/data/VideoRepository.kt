package com.danitube.app.data

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
    private val storage: VideoStorage,
    private val backup: BackupManager,
) {
    val videos: Flow<List<VideoEntity>> = dao.observeAll()

    suspend fun add(videos: List<VideoEntity>) = dao.upsert(videos)

    suspend fun all(): List<VideoEntity> = dao.getAll()

    suspend fun recordPlay(id: String) = dao.recordPlay(id, System.currentTimeMillis())

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao.get(id)?.let { v ->
            storage.delete(v.videoFile)
            v.thumbFile?.let(storage::delete)
        }
        dao.delete(id)
        runCatching { backup.sync(removedId = id) }
    }
}
