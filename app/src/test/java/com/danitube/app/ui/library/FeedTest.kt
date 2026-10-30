package com.danitube.app.ui.library

import com.danitube.app.data.db.VideoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedTest {
    private fun item(id: String, short: Boolean = false) =
        VideoItem(id = id, title = id, durationSec = 60, thumb = null, video = null, isShort = short)

    /** v1 v2 ... as "V", a shelf as "S(n)". */
    private fun layout(items: List<VideoItem>) = feed(items).joinToString(" ") {
        when (it) {
            is FeedEntry.Video -> it.item.id
            is FeedEntry.Shelf -> "S${it.items.size}"
        }
    }

    @Test fun noShortsMeansNoShelf() {
        assertEquals("a b c", layout(listOf(item("a"), item("b"), item("c"))))
    }

    @Test fun shelfComesAfterTwoVideos() {
        val items = listOf(item("a"), item("s1", true), item("b"), item("c"), item("s2", true))
        assertEquals("a b S2 c", layout(items))
    }

    @Test fun fewVideosPutTheShelfAtTheEnd() {
        assertEquals("a S1", layout(listOf(item("a"), item("s", true))))
    }

    @Test fun onlyShortsStillShowAShelf() {
        assertEquals("S3", layout(listOf(item("x", true), item("y", true), item("z", true))))
    }

    @Test fun longShelvesAreSplitAndSpreadOut() {
        val shorts = (1..10).map { item("s$it", true) }
        val videos = (1..12).map { item("v$it") }
        assertEquals("v1 v2 S8 v3 v4 v5 v6 v7 v8 v9 v10 S2 v11 v12", layout(videos + shorts))
    }

    @Test fun shortMeansVerticalAndUpToThreeMinutes() {
        assertEquals(true, VideoEntity.shapeIsShort(1080, 1920, 45))
        assertEquals(false, VideoEntity.shapeIsShort(1920, 1080, 45))
        assertEquals(false, VideoEntity.shapeIsShort(1080, 1920, 600))
        assertNull(VideoEntity.shapeIsShort(0, 0, 45))
    }
}
