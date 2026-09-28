package com.cappyjune.playa.desktop.ui.offline

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cappyjune.playa.desktop.data.Downloads
import com.cappyjune.playa.desktop.playback.Playback
import com.cappyjune.playa.desktop.playback.Resume
import com.cappyjune.playa.desktop.ui.Section
import com.cappyjune.playa.desktop.ui.SectionTitle
import com.cappyjune.playa.desktop.ui.jellyfin.HeaderText
import com.cappyjune.playa.desktop.ui.jellyfin.HoverCard
import com.cappyjune.playa.desktop.ui.jellyfin.JfBack
import com.cappyjune.playa.desktop.ui.jellyfin.PageHeader
import com.cappyjune.playa.desktop.ui.jellyfin.Picture
import com.cappyjune.playa.desktop.ui.jellyfin.size

/** What's on this computer to watch with no connection: films, and shows with their episodes. */
@Composable
fun OfflineScreen() {
    val all by Downloads.items.collectAsState()
    var series by rememberSaveable { mutableStateOf<String?>(null) }
    var remove by remember { mutableStateOf<Downloads.Item?>(null) }
    var sweep by remember { mutableStateOf(false) }
    JfBack.handler = { if (series != null) { series = null; true } else false }
    val points = remember(Playback.closed, all) { Resume.all.toMap() }
    val done = all.values.filter { it.done }
    val pending = all.values.filter { !it.done }.sortedBy { it.addedAt }
    val films = done.filter { it.type != "Episode" }.sortedByDescending { it.addedAt }
    val shows = done.filter { it.type == "Episode" }.groupBy { it.seriesId ?: it.seriesName.orEmpty() }
        .mapValues { (_, e) -> e.sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: 0 })) }
    val used = remember(done.size) { Downloads.bytesUsed() }
    val play = { list: List<Downloads.Item>, i: Int -> Playback.play(list.map { it.toMedia() }, i) }

    Column(Modifier.fillMaxSize()) {
        val s = series
        PageHeader(if (s != null) ({ series = null }) else null, { if (s != null) HeaderText(shows[s]?.firstOrNull()?.seriesName.orEmpty()) else SectionTitle(Section.OFFLINE) }) {
            if (s == null && done.any { Downloads.isWatched(it.id) }) TextButton(onClick = { sweep = true }) { Icon(Icons.Rounded.DeleteSweep, null); Text("  Delete watched") }
        }
        val state = rememberLazyGridState()
        Box(Modifier.fillMaxSize()) {
            if (s != null) {
                val eps = shows[s].orEmpty()
                LazyVerticalGrid(GridCells.Adaptive(560.dp), state = state, contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
                    items(eps.size) { i ->
                        val e = eps[i]
                        Row(Modifier.clip(RoundedCornerShape(16.dp)).clickable { play(eps, i) }.pointerHoverIcon(PointerIcon.Hand).padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(220.dp).aspectRatio(16 / 9f)) {
                                Picture((e.still ?: e.poster)?.let { java.io.File(it).toURI().toString() }, Modifier.fillMaxSize())
                                Progress(points[e.key], Modifier.align(Alignment.BottomCenter))
                                if (Downloads.isWatched(e.id)) Watched(Modifier.align(Alignment.TopEnd))
                            }
                            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                                Text(listOfNotNull(e.code, e.title).joinToString("  "), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(listOf(e.quality.label, size(e.bytes)).joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                e.overview?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            IconButton(onClick = { remove = e }) { Icon(Icons.Rounded.Delete, "Remove download") }
                        }
                    }
                }
            } else LazyVerticalGrid(GridCells.Adaptive(160.dp), state = state, contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 4.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(if (all.isEmpty()) "Nothing downloaded yet. Use the download button on a film or episode in Jellyfin."
                        else "${done.size} downloaded · ${size(used)} on this computer",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                }
                if (pending.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { Heading("Downloading") }
                    pending.forEach { d -> item(span = { GridItemSpan(maxLineSpan) }, key = "p-${d.id}") { PendingRow(d) } }
                }
                if (films.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { Heading("Films") }
                    items(films, key = { it.id }) { f ->
                        Poster(f.poster, f.title, listOfNotNull(f.year?.toString(), f.quality.label, size(f.bytes)).joinToString(" · "), points[f.key], Downloads.isWatched(f.id)) { play(listOf(f), 0) }
                    }
                }
                if (shows.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { Heading("Shows") }
                    items(shows.entries.toList(), key = { it.key }) { (key, eps) ->
                        Poster(eps.first().poster, eps.first().seriesName.orEmpty(), "${eps.size} episode${if (eps.size > 1) "s" else ""}", null, false) { series = key }
                    }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
    remove?.let { d ->
        AlertDialog(onDismissRequest = { remove = null }, title = { Text("Remove the download?") }, text = { Text(d.title) },
            confirmButton = { TextButton(onClick = { Downloads.delete(listOf(d.id)); remove = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { remove = null }) { Text("Keep") } })
    }
    if (sweep) AlertDialog(onDismissRequest = { sweep = false }, title = { Text("Delete everything you've watched?") },
        text = { Text("${done.count { Downloads.isWatched(it.id) }} downloads you've played to the end.") },
        confirmButton = { TextButton(onClick = { Downloads.deleteWatched(); sweep = false }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { sweep = false }) { Text("Keep") } })
}

@Composable
private fun Heading(t: String) = Text(t, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

@Composable
private fun Watched(modifier: Modifier) = Icon(Icons.Rounded.Check, "Watched", modifier.padding(6.dp)
    .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape).padding(3.dp), tint = MaterialTheme.colorScheme.onPrimary)

@Composable
private fun Progress(p: Resume.Point?, modifier: Modifier) {
    if (p == null || p.duration <= 0 || p.position >= p.duration * 0.95) return
    LinearProgressIndicator(progress = { (p.position / p.duration).coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().padding(8.dp).height(4.dp), drawStopIndicator = {})
}

@Composable
private fun Poster(file: String?, title: String, sub: String, point: Resume.Point?, watched: Boolean, onClick: () -> Unit) {
    HoverCard(Modifier.fillMaxWidth(), onClick) {
        Box(Modifier.fillMaxWidth().aspectRatio(2 / 3f)) {
            Picture(file?.let { java.io.File(it).toURI().toString() }, Modifier.fillMaxSize())
            Progress(point, Modifier.align(Alignment.BottomCenter))
            if (watched) Watched(Modifier.align(Alignment.TopEnd))
        }
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun PendingRow(d: Downloads.Item) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(d.seriesName?.let { "$it · ${listOfNotNull(d.code, d.title).joinToString("  ")}" } ?: d.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            when (d.state) {
                Downloads.State.QUEUED -> Text("Waiting · ${d.quality.label}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Downloads.State.DOWNLOADING -> {
                    Text("${d.quality.label} · ${size(d.bytes)}${if (d.total > 0) " of ${size(d.total)}" else ""}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(progress = { d.percent / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), drawStopIndicator = {})
                }
                Downloads.State.FAILED -> Text("Failed: ${d.error ?: "unknown error"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                else -> {}
            }
        }
        if (d.state == Downloads.State.FAILED) IconButton(onClick = { Downloads.retry(d.id) }) { Icon(Icons.Rounded.Refresh, "Retry") }
        IconButton(onClick = { Downloads.delete(listOf(d.id)) }) { Icon(Icons.Rounded.Close, "Cancel") }
    }
}
