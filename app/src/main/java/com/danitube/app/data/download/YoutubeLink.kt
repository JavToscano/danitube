package com.danitube.app.data.download

import java.net.URI

/** A YouTube link normalised to something yt-dlp accepts. */
data class YoutubeLink(val url: String, val isPlaylist: Boolean) {
    companion object {
        private val urlRegex = Regex("""https?://\S+""")
        private val hosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")
        private val idRegex = Regex("""[A-Za-z0-9_-]{6,20}""")

        /**
         * Pulls the first YouTube URL out of free text (the share sheet often sends
         * "Title https://youtu.be/xyz"). Returns null when there is none.
         */
        fun parse(text: String): YoutubeLink? {
            for (match in urlRegex.findAll(text)) {
                val uri = runCatching { URI(match.value.trimEnd('.', ',', ')')) }.getOrNull() ?: continue
                val host = uri.host?.lowercase() ?: continue
                if (host !in hosts) continue
                val query = parseQuery(uri.rawQuery)
                val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }

                val videoId = when {
                    host == "youtu.be" -> segments.firstOrNull()
                    segments.firstOrNull() == "watch" -> query["v"]
                    segments.firstOrNull() in setOf("shorts", "live", "embed") -> segments.getOrNull(1)
                    else -> null
                }?.takeIf { idRegex.matches(it) }
                if (videoId != null) {
                    return YoutubeLink("https://www.youtube.com/watch?v=$videoId", isPlaylist = false)
                }

                val listId = query["list"]?.takeIf { idRegex.matches(it) }
                if (listId != null && segments.firstOrNull() == "playlist") {
                    return YoutubeLink("https://www.youtube.com/playlist?list=$listId", isPlaylist = true)
                }
            }
            return null
        }

        private fun parseQuery(raw: String?): Map<String, String> =
            raw.orEmpty().split('&').mapNotNull {
                val i = it.indexOf('=')
                if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
            }.toMap()
    }
}
