package com.danitube.app.ui.library

import android.net.Uri
import com.danitube.app.data.VideoRepository
import com.danitube.app.data.db.SuggestionDao
import com.danitube.app.data.db.SuggestionEntity
import com.danitube.app.data.download.VideoStorage
import com.danitube.app.data.suggest.SuggestionSettings
import com.danitube.app.data.suggest.UnmeteredNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Library videos and suggested YouTube videos, each resolved to what the UI needs. */
private data class Sources(val library: List<VideoItem>, val streamed: List<VideoItem>)

/**
 * Everything she can watch: the library videos, plus suggested YouTube videos mixed in while the
 * phone is on unmetered Wi-Fi and the parent has suggestions switched on.
 */
@Singleton
class VideoCatalog @Inject constructor(
    repository: VideoRepository,
    suggestions: SuggestionDao,
    private val storage: VideoStorage,
    network: UnmeteredNetwork,
    settings: SuggestionSettings,
) {
    private val sources: Flow<Sources> = combine(
        repository.videos,
        suggestions.observeStreamable(),
        storage.version, // files moved between the app and the library folder
    ) { videos, streams, _ ->
        val ids = videos.map { it.id }.toSet()
        Sources(
            library = videos.mapNotNull { it.toItem(storage) },
            streamed = streams.filter { it.id !in ids }.map { it.toItem() },
        )
    }.flowOn(Dispatchers.IO) // resolving a Uri may list the library folder

    /** What the kid screens show. Streamed videos only appear on Wi-Fi with suggestions on. */
    val items: Flow<List<VideoItem>> =
        combine(sources, settings.enabled, network.available) { s, enabled, wifi ->
            if (enabled && wifi) mix(s.library, s.streamed) else s.library
        }

    /** What the parent manages: every video, streamed ones included, whatever the connection. */
    val managed: Flow<List<VideoItem>> = sources.map { it.library + it.streamed }

    suspend fun snapshot(): List<VideoItem> = items.first()

    /** One streamed video after every few library videos, so they feel like part of the feed. */
    private fun mix(library: List<VideoItem>, streamed: List<VideoItem>): List<VideoItem> {
        if (streamed.isEmpty()) return library
        val pending = streamed.toMutableList()
        val result = ArrayList<VideoItem>(library.size + streamed.size)
        library.forEachIndexed { index, item ->
            result += item
            if ((index + 1) % EVERY == 0 && pending.isNotEmpty()) result += pending.removeAt(0)
        }
        return result + pending
    }

    private fun SuggestionEntity.toItem() = VideoItem(
        id = id,
        title = title,
        durationSec = durationSec,
        thumb = Uri.parse("https://i.ytimg.com/vi/$id/mqdefault.jpg"),
        video = null,
        playCount = playCount,
        isStream = true,
    )

    private companion object {
        const val EVERY = 3
    }
}
