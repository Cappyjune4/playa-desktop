package com.cappyjune.playa.desktop.ui.jellyfin

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.rounded.Download
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cappyjune.playa.desktop.data.JfApi
import com.cappyjune.playa.desktop.data.JfItem
import com.cappyjune.playa.desktop.playback.Playback
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** A film, show, season, episode or collection: artwork, what it is, and ways to play it. */
@Composable
fun ItemScreen(id: String, open: (JfPage) -> Unit, back: () -> Unit) {
    val scope = rememberCoroutineScope()
    val play = rememberJfPlay()
    var item by remember { mutableStateOf<JfItem?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var similar by remember { mutableStateOf<List<JfItem>>(emptyList()) }
    var children by remember { mutableStateOf<List<JfItem>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    // Closing the player: progress and "watched" have moved on.
    LaunchedEffect(id, reload, Playback.closed) {
        runCatching { JfApi.item(id) }.onSuccess { item = it; error = null }.onFailure { if (item == null) error = it.message else Messages.show(it.message ?: "Couldn't reach Jellyfin") }
        item?.let { i ->
            if (i.Type == "BoxSet" || i.Type == "Folder") children = runCatching { JfApi.children(i.Id) }.getOrDefault(emptyList())
            if (similar.isEmpty() && i.Type in setOf("Movie", "Series")) similar = runCatching { JfApi.similar(i.Id) }.getOrDefault(emptyList())
        }
    }
    val i = item
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize()) {
            when {
                i == null && error != null -> Empty("Couldn't load this: $error", Modifier.align(Alignment.Center))
                i == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> {
                    val state = rememberLazyListState()
                    LazyColumn(state = state, contentPadding = PaddingValues(bottom = 40.dp)) {
                        item(key = "top") {
                            Top(i, open, onPlay = { fromStart -> play(i, fromStart) },
                                onFavourite = { scope.launch { runCatching { JfApi.setFavourite(i.Id, !i.favourite) }.onSuccess { reload++ } } },
                                onPlayed = { scope.launch { runCatching { JfApi.setPlayed(i.Id, !i.played) }.onSuccess { reload++ } } })
                        }
                        if (i.Type == "Series" || i.Type == "Season") item(key = "eps") { Episodes(i, open) }
                        if (children.isNotEmpty()) carousel(if (i.Type == "BoxSet") "In this collection" else "Inside") {
                            items(children, key = { it.Id }) { c -> PosterCard(c, onClick = { open(JfPage.Item(c.Id)) }) }
                        }
                        val cast = i.People.orEmpty().filter { it.Type in setOf("Actor", "GuestStar") }.take(30)
                        if (cast.isNotEmpty()) carousel("Cast") {
                            items(cast, key = { "${it.Id}-${it.Role}" }) { p -> PersonCard(p) { p.Id?.let { open(JfPage.Person(it, p.Name.orEmpty())) } } }
                        }
                        if (similar.isNotEmpty()) carousel("More like this") {
                            items(similar, key = { it.Id }) { s -> PosterCard(s, onClick = { open(JfPage.Item(s.Id)) }) }
                        }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                }
            }
            IconButton(onClick = back, Modifier.padding(16.dp),
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.4f), contentColor = Color.White)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
            }
        }
    }
}

/**
 * The backdrop across the top, fading into the page; the poster and everything about the title
 * side by side over its lower part, as Jellyfin's web client lays it out on a wide screen.
 */
@Composable
private fun Top(i: JfItem, open: (JfPage) -> Unit, onPlay: (Boolean) -> Unit, onFavourite: () -> Unit, onPlayed: () -> Unit) {
    val bg = MaterialTheme.colorScheme.surface
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val heroHeight = (maxWidth * 0.42f).coerceIn(260.dp, 560.dp)
        Box(Modifier.fillMaxWidth().height(heroHeight)) {
            Picture(JfApi.backdrop(i, 1920), Modifier.fillMaxSize(), corner = 0.dp)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to bg)))
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to bg.copy(alpha = 0.7f), 0.5f to Color.Transparent)))
        }
        val wide = maxWidth >= 900.dp
        Row(Modifier.padding(start = 40.dp, end = 40.dp, top = heroHeight - if (wide) 200.dp else 120.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            if (wide) {
                val poster = if (i.Type == "Episode") null else JfApi.poster(i, 500)
                if (poster != null) Picture(poster, Modifier.width(240.dp).aspectRatio(2 / 3f))
            }
            Column(Modifier.weight(1f).widthIn(max = 900.dp).padding(top = if (wide) 24.dp else 0.dp)) {
                if (i.Type == "Episode" && i.SeriesName != null) Text(i.SeriesName, style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { i.SeriesId?.let { open(JfPage.Item(it)) } })
                Text(if (i.Type == "Episode") listOfNotNull(i.episodeCode, i.Name).joinToString("  ") else i.Name.orEmpty(),
                    style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Gap(6.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    i.CommunityRating?.let {
                        Icon(Icons.Rounded.Star, null, Modifier.size(18.dp), tint = Color(0xFFFFC107))
                        Text(" %.1f     ".format(it), style = MaterialTheme.typography.bodyLarge)
                    }
                    MetaDot(listOf(
                        if (i.Type == "Series") listOfNotNull(i.ProductionYear?.toString(), if (i.Status == "Continuing") "Present" else i.EndDate?.take(4)).joinToString("–") else i.ProductionYear?.toString(),
                        i.RunTimeTicks?.takeIf { i.playable }?.let { runtime(i.runtimeMs) },
                        i.OfficialRating,
                        i.RunTimeTicks?.takeIf { i.playable }?.let { "Ends ${endsAt(i.runtimeMs - i.resumeMs)}" },
                    ))
                }
                if (!i.Genres.isNullOrEmpty()) { Gap(4.dp); Text(i.Genres.take(5).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Gap(20.dp)
                Actions(i, onPlay, onFavourite, onPlayed)
                techLine(i)?.let { Gap(14.dp); Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                i.Taglines?.firstOrNull()?.let { Gap(14.dp); Text(it, style = MaterialTheme.typography.titleMedium, fontStyle = FontStyle.Italic) }
                i.Overview?.let { Gap(10.dp); Text(it, style = MaterialTheme.typography.bodyLarge) }
                val crew = i.People.orEmpty().filter { it.Type in setOf("Director", "Writer") }.distinctBy { it.Name }
                if (crew.isNotEmpty()) {
                    Gap(12.dp)
                    crew.groupBy { it.Type }.forEach { (role, ps) ->
                        Text("${if (role == "Director") "Directed by" else "Written by"} ${ps.joinToString { it.Name.orEmpty() }}",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun Tip(text: String, content: @Composable () -> Unit) =
    TooltipBox(TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above), tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState()) { content() }

@Composable
private fun Actions(i: JfItem, onPlay: (Boolean) -> Unit, onFavourite: () -> Unit, onPlayed: () -> Unit) {
    val resumable = i.playable && i.resumeMs > 15_000 && !i.played
    Column(Modifier.widthIn(max = 520.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (i.playable || i.Type == "Series" || i.Type == "Season") Button(onClick = { onPlay(false) }, Modifier.height(48.dp),
                contentPadding = PaddingValues(horizontal = 24.dp)) {
                Icon(Icons.Rounded.PlayArrow, null)
                Text(when { resumable -> " Resume ${fmt(i.resumeMs)}"; i.Type == "Series" || i.Type == "Season" -> " Play next episode"; else -> " Play" },
                    style = MaterialTheme.typography.titleSmall)
            }
            if (resumable) Tip("Play from the start") { FilledTonalIconButton(onClick = { onPlay(true) }, Modifier.size(48.dp)) { Icon(Icons.Rounded.Replay, "Play from the start") } }
            Tip(if (i.played) "Mark unwatched" else "Mark watched") {
                FilledTonalIconButton(onClick = onPlayed, Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Check, if (i.played) "Mark unwatched" else "Mark watched", tint = if (i.played) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (i.playable) Tip("Download for offline") { DownloadButton(i) }
            Tip(if (i.favourite) "Remove from favourites" else "Add to favourites") {
                FilledTonalIconButton(onClick = onFavourite, Modifier.size(48.dp)) {
                    Icon(if (i.favourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favourite",
                        tint = if (i.favourite) Color(0xFFEF5350) else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ItemMenuButton(i)
        }
        if (resumable && i.runtimeMs > 0) {
            Gap(10.dp)
            LinearProgressIndicator(progress = { (i.resumeMs.toFloat() / i.runtimeMs).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(4.dp), drawStopIndicator = {})
            Text("${runtime(i.runtimeMs - i.resumeMs)} left", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Seasons as chips, then that season's episodes with their stills and progress. */
@Composable
private fun Episodes(i: JfItem, open: (JfPage) -> Unit) {
    val play = rememberJfPlay()
    val seriesId = if (i.Type == "Series") i.Id else i.SeriesId ?: i.Id
    var seasons by remember { mutableStateOf<List<JfItem>>(emptyList()) }
    var season by rememberSaveable(i.Id) { mutableStateOf(if (i.Type == "Season") i.Id else null) }
    var eps by remember { mutableStateOf<List<JfItem>?>(null) }
    LaunchedEffect(seriesId, Playback.closed) {
        seasons = runCatching { JfApi.seasons(seriesId) }.getOrDefault(emptyList())
        // Open on the season with the next unwatched episode, as Jellyfin's apps do.
        if (season == null) season = seasons.firstOrNull { (it.UserData?.UnplayedItemCount ?: 0) > 0 }?.Id ?: seasons.firstOrNull()?.Id
    }
    LaunchedEffect(season, Playback.closed) { season?.let { s -> eps = runCatching { JfApi.episodes(seriesId, s) }.getOrDefault(emptyList()) } }
    Column(Modifier.padding(top = 32.dp)) {
        if (seasons.size > 1 && i.Type == "Series") LazyRow(contentPadding = PaddingValues(horizontal = 40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(seasons, key = { it.Id }) { s -> ItemMenuArea(s) { FilterChip(s.Id == season, { season = s.Id }, label = { Text(s.Name.orEmpty()) }) } }
        }
        Gap(8.dp)
        val e = eps
        val downloads by com.cappyjune.playa.desktop.data.Downloads.items.collectAsState()
        if (e == null) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else {
            val missing = e.filter { downloads[it.Id] == null }
            var picking by remember { mutableStateOf(false) }
            if (missing.isNotEmpty()) Box(Modifier.padding(horizontal = 28.dp)) {
                androidx.compose.material3.TextButton(onClick = { picking = true }) {
                    Icon(androidx.compose.material.icons.Icons.Rounded.Download, null)
                    Text(" Download ${if (missing.size == e.size) "this season" else "the other ${missing.size}"}")
                }
                QualityMenu(missing, picking) { picking = false }
            }
        }
        if (e != null) e.forEach { ep -> EpisodeRow(ep, onPlay = { play(ep, false) }, onOpen = { open(JfPage.Item(ep.Id)) }) }
    }
}

/** Click plays it; the name opens its own page. */
@Composable
private fun EpisodeRow(ep: JfItem, onPlay: () -> Unit, onOpen: () -> Unit) = ItemMenuArea(ep) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp).widthIn(max = 1200.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onPlay).pointerHoverIcon(PointerIcon.Hand)
        .padding(horizontal = 16.dp, vertical = 10.dp)) {
        Box(Modifier.width(240.dp).aspectRatio(16 / 9f)) {
            Picture(JfApi.still(ep, 480), Modifier.fillMaxSize())
            if (ep.played) Icon(Icons.Rounded.Check, "Watched", Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape).padding(3.dp), tint = MaterialTheme.colorScheme.onPrimary)
            val pct = ep.UserData?.PlayedPercentage
            if (!ep.played && pct != null && pct > 0) LinearProgressIndicator(progress = { (pct / 100).toFloat() },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp).height(4.dp), drawStopIndicator = {})
        }
        Column(Modifier.padding(start = 20.dp).weight(1f)) {
            Text("${ep.IndexNumber?.let { "$it. " } ?: ""}${ep.Name.orEmpty()}", style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (ep.played) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                modifier = Modifier.clickable(onClick = onOpen))
            Text(listOfNotNull(ep.RunTimeTicks?.let { runtime(ep.runtimeMs) }, ep.PremiereDate?.take(10)).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ep.Overview?.let { Gap(4.dp); Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        DownloadButton(ep, tonal = false)
    }
}


/** "4K · HDR10 · HEVC · DTS 5.1 · 3 subtitles", from the first version on the server. */
private fun techLine(i: JfItem): String? {
    val src = i.MediaSources?.firstOrNull() ?: return null
    val streams = src.MediaStreams.orEmpty()
    val v = streams.firstOrNull { it.Type == "Video" }
    val a = streams.firstOrNull { it.Type == "Audio" && it.IsDefault } ?: streams.firstOrNull { it.Type == "Audio" }
    val subs = streams.count { it.Type == "Subtitle" }
    val res = v?.Width?.let { w -> when { w >= 3200 -> "4K"; w >= 1800 -> "1080p"; w >= 1200 -> "720p"; else -> "SD" } }
    val hdr = v?.VideoRangeType?.takeIf { it != "SDR" && it != "Unknown" }
    val ch = a?.Channels?.let { when (it) { 1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"; else -> "$it ch" } }
    return listOfNotNull(res, hdr, v?.Codec?.uppercase(), listOfNotNull(a?.Codec?.uppercase(), ch).joinToString(" ").ifBlank { null },
        if (subs > 0) "$subs subtitle${if (subs > 1) "s" else ""}" else null,
        src.Size?.let { "%.1f GB".format(it / 1e9) }).joinToString(" · ").ifBlank { null }
}

private fun runtime(ms: Long): String { val m = ms / 60_000; return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m" }
private fun endsAt(remainingMs: Long): String = LocalTime.now().plusSeconds(remainingMs / 1000).format(DateTimeFormatter.ofPattern("h:mm a"))
private fun fmt(ms: Long): String { val s = ms / 1000; return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }
