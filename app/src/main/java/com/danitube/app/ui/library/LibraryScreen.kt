package com.danitube.app.ui.library

import androidx.compose.ui.platform.LocalContext
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.danitube.app.R

/** Home feed: one wide card per video (phones), more columns on tablets. */
@Composable
fun LibraryScreen(
    onVideoClick: (videoId: String) -> Unit,
    modifier: Modifier = Modifier,
    query: String = "",
    favoritesOnly: Boolean = false,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val all by viewModel.videos.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val videos = if (favoritesOnly) favorites else all
    val shown = videos?.filter { it.title.contains(query.trim(), ignoreCase = true) }

    when {
        shown == null -> Unit
        videos!!.isEmpty() -> if (favoritesOnly) {
            EmptyState(R.string.favorites_empty_title, R.string.favorites_empty_hint, modifier)
        } else {
            EmptyState(R.string.library_empty_title, R.string.library_empty_hint, modifier)
        }
        shown.isEmpty() -> NoResults(modifier)
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 340.dp),
            modifier = modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            feed(shown).forEach { entry ->
                when (entry) {
                    is FeedEntry.Video -> item(key = entry.item.id) {
                        VideoCard(entry.item, showPlays = favoritesOnly, onClick = { onVideoClick(entry.item.id) })
                    }
                    // The shelf takes the whole row, like YouTube's Shorts shelf.
                    is FeedEntry.Shelf -> item(key = entry.key, span = { GridItemSpan(maxLineSpan) }) {
                        ShortsShelf(entry.items, onClick = { onVideoClick(it.id) })
                    }
                }
            }
        }
    }
}

/** One row of the feed: a wide video card, or a shelf of Shorts. */
internal sealed interface FeedEntry {
    data class Video(val item: VideoItem) : FeedEntry
    data class Shelf(val key: String, val items: List<VideoItem>) : FeedEntry
}

private const val SHELF_SIZE = 8
private const val FIRST_SHELF_AFTER = 2
private const val SHELF_EVERY = 8

/**
 * Like YouTube: regular videos are wide cards and the Shorts are grouped into shelves of tall
 * cards, the first one after a couple of videos and then one every few more. Order is kept.
 */
internal fun feed(items: List<VideoItem>): List<FeedEntry> {
    val (shorts, videos) = items.partition { it.isShort }
    if (shorts.isEmpty()) return videos.map(FeedEntry::Video)
    val shelves = shorts.chunked(SHELF_SIZE).mapIndexed { i, chunk -> FeedEntry.Shelf("shelf-$i", chunk) }.toMutableList()
    val result = ArrayList<FeedEntry>(videos.size + shelves.size)
    videos.forEachIndexed { index, video ->
        result += FeedEntry.Video(video)
        val atShelfSlot = index + 1 >= FIRST_SHELF_AFTER && (index + 1 - FIRST_SHELF_AFTER) % SHELF_EVERY == 0
        if (atShelfSlot && shelves.isNotEmpty()) result += shelves.removeAt(0)
    }
    return result + shelves // few videos: the rest of the shelves go at the end
}

/** "Shorts" heading and a row of tall cards you swipe sideways. */
@Composable
private fun ShortsShelf(items: List<VideoItem>, onClick: (VideoItem) -> Unit) {
    Column {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.nav_shorts),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.id }) { ShortCard(it, onClick = { onClick(it) }) }
        }
    }
}

@Composable
private fun ShortCard(item: VideoItem, onClick: () -> Unit) {
    Box(
        Modifier
            .width(168.dp)
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = item.thumb,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(96.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))),
        )
        Text(
            item.title,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
        )
    }
}

@Composable
private fun NoResults(modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.no_results),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyState(@StringRes title: Int, @StringRes hint: Int, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 20.dp))
        Text(
            stringResource(hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun VideoCard(item: VideoItem, showPlays: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceVariant)) {
            AsyncImage(
                model = item.thumb,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (item.durationSec > 0) {
                Text(
                    formatDuration(item.durationSec),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChannelAvatar(size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(
                    if (showPlays) {
                        LocalContext.current.resources.getQuantityString(R.plurals.played_times, item.playCount, item.playCount)
                    } else {
                        "DaniTube"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** Round "channel" picture used in the feed and the shorts overlay. */
@Composable
fun ChannelAvatar(size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text("D", color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

internal fun formatDuration(totalSec: Int): String {
    val h = totalSec / 3600
    val m = totalSec % 3600 / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
