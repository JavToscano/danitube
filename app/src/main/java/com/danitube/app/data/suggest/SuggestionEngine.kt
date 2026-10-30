package com.danitube.app.data.suggest

import android.util.Log
import com.danitube.app.data.db.SuggestionDao
import com.danitube.app.data.db.SuggestionEntity
import com.danitube.app.data.db.VideoDao
import com.danitube.app.data.db.VideoEntity
import com.danitube.app.data.download.DownloadScheduler
import com.danitube.app.data.download.VideoDetails
import com.danitube.app.data.download.VideoStorage
import com.danitube.app.data.download.YtDlpDownloader
import com.danitube.app.data.download.YoutubeLink
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds videos like the ones she watches. Everything here runs on unmetered Wi-Fi only.
 *
 * What she likes comes from the tags and channels of her library, with the videos she plays
 * counting more (a video the parent added counts once; every play adds one). The top tags and
 * channel become YouTube searches; each hit is checked (not live, not age-restricted, not long)
 * and scored by how many liked tags it shares. The best ones are stored as suggestions, which can
 * be streamed, and a few are downloaded for good each run.
 */
@Singleton
class SuggestionEngine @Inject constructor(
    private val videos: VideoDao,
    private val suggestions: SuggestionDao,
    private val downloader: YtDlpDownloader,
    private val scheduler: DownloadScheduler,
    private val storage: VideoStorage,
    private val settings: SuggestionSettings,
    private val network: UnmeteredNetwork,
) {
    /** One pass: fill in missing tags, look for new videos, download the best few. */
    suspend fun run() {
        if (!settings.enabled.value || !network.isAvailable()) return
        backfillTags()
        refresh()
        autoDownload()
    }

    /** Videos downloaded before this feature have no tags; fetch them a few at a time. */
    private suspend fun backfillTags() {
        for (video in videos.withoutTags(BACKFILL_PER_RUN)) {
            if (!network.isAvailable()) return
            try {
                val details = downloader.details(video.id)
                videos.setMeta(video.id, details.channel, VideoEntity.joinTags(details.tags))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "No tags for ${video.id}", e)
            }
        }
    }

    private suspend fun refresh() {
        val profile = profile()
        if (profile.isEmpty()) return
        suggestions.deleteStale(System.currentTimeMillis() - STALE_AFTER_MS)

        val known = (videos.getAll().map { it.id } + suggestions.getAll().map { it.id }).toSet()
        val candidates = profile.queries().flatMap { query ->
            try {
                downloader.search(query, HITS_PER_QUERY)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Search failed for $query", e)
                emptyList()
            }
        }
            .distinctBy { it.id }
            .filter { it.id !in known && it.durationSec in MIN_DURATION_SEC..MAX_DURATION_SEC }
            .take(MAX_CHECKED)

        var added = 0
        for (candidate in candidates) {
            if (added >= MAX_NEW_PER_RUN || !network.isAvailable()) break
            val details = try {
                downloader.details(candidate.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                continue
            }
            if (!isSuitable(details)) continue
            val score = profile.score(details.channel, terms(details.title, details.tags))
            if (score <= 0f) continue
            suggestions.upsert(
                SuggestionEntity(
                    id = details.id,
                    title = details.title,
                    channel = details.channel,
                    durationSec = details.durationSec,
                    tags = VideoEntity.joinTags(details.tags),
                    score = score,
                    addedAt = System.currentTimeMillis(),
                    status = SuggestionEntity.STATUS_SUGGESTED,
                    isShort = details.isShort,
                ),
            )
            added++
        }
    }

    private fun isSuitable(d: VideoDetails) =
        !d.isLive && d.ageLimit == 0 && d.durationSec in MIN_DURATION_SEC..MAX_DURATION_SEC

    /** Queues the best suggestions (the ones she already played first) for a Wi-Fi download. */
    private suspend fun autoDownload() {
        if (storage.freeBytes() < MIN_FREE_BYTES) return
        val local = videos.getAll().map { it.id }.toSet()
        suggestions.streamable()
            .filter { it.id !in local }
            .sortedByDescending { it.playCount * PLAY_WEIGHT + it.score }
            .take(MAX_DOWNLOADS_PER_RUN)
            .forEach { scheduler.enqueue(YoutubeLink("https://www.youtube.com/watch?v=${it.id}", false), unmeteredOnly = true) }
    }

    private suspend fun profile(): Profile {
        val tags = mutableMapOf<String, Float>()
        val channels = mutableMapOf<String, Float>()
        fun add(weight: Float, channel: String?, terms: List<String>) {
            terms.forEach { tags.merge(it, weight, Float::plus) }
            channel?.let { channels.merge(it, weight, Float::plus) }
        }
        videos.getAll().forEach { add(1f + it.playCount, it.channel, terms(it.title, it.tagList)) }
        // Streamed videos only count once she has watched them.
        suggestions.getAll().filter { it.playCount > 0 && it.status != SuggestionEntity.STATUS_REJECTED }
            .forEach { add(it.playCount.toFloat(), it.channel, terms(it.title, it.tagList)) }
        return Profile(tags, channels)
    }

    private class Profile(val tags: Map<String, Float>, val channels: Map<String, Float>) {
        fun isEmpty() = tags.isEmpty() && channels.isEmpty()

        /** Up to three searches: her favourite channel, then her favourite tags two at a time. */
        fun queries(): List<String> {
            val topTags = tags.entries.sortedByDescending { it.value }.map { it.key }
            return listOfNotNull(
                channels.maxByOrNull { it.value }?.key,
                topTags.take(2).joinToString(" ").ifBlank { null },
                topTags.drop(2).take(2).joinToString(" ").ifBlank { null },
            ).distinct()
        }

        fun score(channel: String?, terms: List<String>): Float =
            terms.distinct().sumOf { (tags[it] ?: 0f).toDouble() }.toFloat() +
                (channel?.let { channels[it] } ?: 0f) * CHANNEL_WEIGHT
    }

    private companion object {
        const val TAG = "SuggestionEngine"
        const val BACKFILL_PER_RUN = 10
        const val HITS_PER_QUERY = 10
        const val MAX_CHECKED = 15
        const val MAX_NEW_PER_RUN = 10
        const val MAX_DOWNLOADS_PER_RUN = 3
        const val MIN_DURATION_SEC = 30
        const val MAX_DURATION_SEC = 25 * 60
        const val MIN_FREE_BYTES = 1_500_000_000L
        const val STALE_AFTER_MS = 14L * 24 * 60 * 60 * 1000
        const val PLAY_WEIGHT = 100f
        const val CHANNEL_WEIGHT = 2f

        /** Words that say nothing about the kind of video. */
        val STOPWORDS = setOf(
            "youtube", "video", "videos", "shorts", "short", "official", "oficial", "new", "nuevo",
            "para", "con", "los", "las", "del", "una", "the", "and", "for", "with", "hd", "full",
        )
        val WORDS = Regex("""[\p{L}\p{N}]+""")

        /** Tags when the video has them, otherwise the words of its title. Lower case, no filler. */
        fun terms(title: String, tags: List<String>): List<String> {
            val source = tags.ifEmpty { WORDS.findAll(title).map { it.value }.toList() }
            return source.map { it.trim().lowercase() }.filter { it.length in 3..40 && it !in STOPWORDS }
        }
    }
}
