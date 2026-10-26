package com.danitube.app.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeLinkTest {
    private val watch = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

    @Test fun watchUrl() = assertEquals(watch, YoutubeLink.parse(watch)?.url)

    @Test fun mobileWithExtras() =
        assertEquals(watch, YoutubeLink.parse("https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=30s&feature=share")?.url)

    @Test fun shortLink() = assertEquals(watch, YoutubeLink.parse("https://youtu.be/dQw4w9WgXcQ?si=abc")?.url)

    @Test fun shorts() = assertEquals(watch, YoutubeLink.parse("https://www.youtube.com/shorts/dQw4w9WgXcQ")?.url)

    @Test fun sharedTextWithTitle() =
        assertEquals(watch, YoutubeLink.parse("Cute song https://youtu.be/dQw4w9WgXcQ")?.url)

    @Test fun videoInsidePlaylistIsSingleVideo() {
        val link = YoutubeLink.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL1234567890")
        assertEquals(watch, link?.url)
        assertFalse(link!!.isPlaylist)
    }

    @Test fun playlist() {
        val link = YoutubeLink.parse("https://www.youtube.com/playlist?list=PL1234567890")
        assertTrue(link!!.isPlaylist)
        assertEquals("https://www.youtube.com/playlist?list=PL1234567890", link.url)
    }

    @Test fun rejectsOtherHostsAndGarbage() {
        assertNull(YoutubeLink.parse("https://example.com/watch?v=dQw4w9WgXcQ"))
        assertNull(YoutubeLink.parse("hello"))
        assertNull(YoutubeLink.parse(""))
    }
}
