package com.danitube.app.data.download

import android.content.Context
import android.media.MediaMetadataRetriever
import com.danitube.app.data.db.VideoDao
import com.danitube.app.data.db.VideoEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Works out which library videos are Shorts (vertical, up to 3 minutes) by reading the size of the
 * video file itself, with no network. New downloads already carry it from yt-dlp; this covers
 * the ones downloaded before the feed had a Shorts shelf.
 */
@Singleton
class VideoShapeDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videos: VideoDao,
    private val storage: VideoStorage,
) {
    suspend fun fillMissing() = withContext(Dispatchers.IO) {
        for (video in videos.withoutShape()) {
            val isShort = shapeOf(video) ?: continue
            videos.setShort(video.id, isShort)
        }
    }

    private fun shapeOf(video: VideoEntity): Boolean? {
        val uri = storage.uri(video.videoFile) ?: return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            fun field(key: Int) = retriever.extractMetadata(key)?.toIntOrNull() ?: 0
            val rotated = field(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) % 180 != 0
            val width = field(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val height = field(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            VideoEntity.shapeIsShort(
                width = if (rotated) height else width,
                height = if (rotated) width else height,
                durationSec = video.durationSec,
            )
        } catch (e: Exception) {
            null // unreadable for now (folder unplugged, bad file); tried again next time
        } finally {
            retriever.release()
        }
    }
}
