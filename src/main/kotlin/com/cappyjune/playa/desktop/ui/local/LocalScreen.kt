package com.cappyjune.playa.desktop.ui.local

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.cappyjune.playa.desktop.data.LocalVideos
import com.cappyjune.playa.desktop.data.Settings
import com.cappyjune.playa.desktop.playback.Playback
import com.cappyjune.playa.desktop.playback.Resume
import com.cappyjune.playa.desktop.ui.Section
import com.cappyjune.playa.desktop.ui.SectionTitle
import com.cappyjune.playa.desktop.ui.jellyfin.HeaderText
import com.cappyjune.playa.desktop.ui.jellyfin.HoverCard
import com.cappyjune.playa.desktop.ui.jellyfin.JfBack
import com.cappyjune.playa.desktop.ui.jellyfin.PageHeader
import java.io.File

/** The videos on this computer: Continue watching, then the folders they're in. */
@Composable
fun LocalScreen() {
    val settings by Settings.state.collectAsState()
    var reload by remember { mutableIntStateOf(0) }
    var videos by remember { mutableStateOf<List<LocalVideos.Video>?>(null) }
    LaunchedEffect(settings.videoFolders, reload) { videos = runCatching { LocalVideos.scan(settings.videoFolders) }.getOrDefault(emptyList()) }
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    JfBack.handler = { when { query.isNotEmpty() -> { query = ""; true }; folder != null -> { folder = null; true }; else -> false } }
    // Resume points move on while the player's up.
    val points = remember(Playback.closed, videos) { Resume.all.toMap() }
    val play = { list: List<LocalVideos.Video>, i: Int -> Playback.play(list.map { it.toMedia() }, i) }
    val searching = query.isNotBlank()

    Column(Modifier.fillMaxSize()) {
        PageHeader(if (folder != null && !searching) ({ folder = null }) else null,
            { if (folder != null && !searching) HeaderText(File(folder!!).name) else SectionTitle(Section.LOCAL) }) {
            OutlinedTextField(query, { query = it }, Modifier.width(300.dp), singleLine = true,
                placeholder = { Text("Search videos") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") } },
                shape = RoundedCornerShape(28.dp))
            IconButton(onClick = { reload++ }) { Icon(Icons.Rounded.Refresh, "Look again") }
            FoldersMenu(settings.videoFolders)
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = { openFiles()?.let { Playback.play(it.map { f -> com.cappyjune.playa.desktop.fileMedia(f) }) } }) {
                Icon(Icons.Rounded.FolderOpen, null); Text("  Open files…")
            }
        }
        Box(Modifier.fillMaxSize()) {
            val all = videos
            val state = rememberLazyGridState()
            when {
                all == null -> CircularProgressIndicator(Modifier.align(Alignment.TopCenter).padding(top = 48.dp))
                all.isEmpty() -> Column(Modifier.padding(40.dp)) {
                    Text("No videos in ${settings.videoFolders.joinToString { File(it).name }} yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Add a folder with the folder button above, or drop video files anywhere on the window.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyVerticalGrid(GridCells.Adaptive(240.dp), state = state, contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 12.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    when {
                        searching -> {
                            val shown = all.filter { it.title.contains(query.trim(), ignoreCase = true) }
                            videos(shown, points, play)
                            if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("Nothing matches.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        folder != null -> videos(all.filter { it.folderPath == folder }, points, play)
                        else -> {
                            // Continue watching: started, not finished, most recent first.
                            val cont = all.mapNotNull { v -> points[v.key]?.takeIf { it.position > 30 && it.position < it.duration * 0.95 }?.let { v to it } }
                                .sortedByDescending { it.second.at }.map { it.first }
                            if (cont.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                                Column {
                                    Heading("Continue watching")
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                        items(cont, key = { it.key }) { v -> VideoCard(v, points[v.key], Modifier.width(280.dp)) { play(cont, cont.indexOf(v)) } }
                                    }
                                }
                            }
                            item(span = { GridItemSpan(maxLineSpan) }) { Heading("Folders") }
                            items(LocalVideos.folders(all), key = { it.path }) { f -> FolderCard(f) { folder = f.path } }
                        }
                    }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

@Composable
private fun Heading(t: String) = Text(t, Modifier.padding(bottom = 10.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

private fun LazyGridScope.videos(list: List<LocalVideos.Video>, points: Map<String, Resume.Point>, play: (List<LocalVideos.Video>, Int) -> Unit) =
    items(list, key = { it.key }) { v -> VideoCard(v, points[v.key]) { play(list, list.indexOf(v)) } }

/** The folders Local looks in: remove one, or add another. */
@Composable
private fun FoldersMenu(folders: List<String>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.CreateNewFolder, "Folders to look in") }
        DropdownMenu(open, { open = false }) {
            Text("Looking in", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            folders.forEach { f ->
                DropdownMenuItem(text = { Text(f.replace(System.getProperty("user.home"), "~")) }, onClick = {},
                    leadingIcon = { Icon(Icons.Rounded.Folder, null) },
                    trailingIcon = { if (folders.size > 1) IconButton(onClick = { Settings.update { s -> s.copy(videoFolders = s.videoFolders - f) } }) { Icon(Icons.Rounded.Close, "Stop looking here") } })
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Add a folder…") }, leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, null) }, onClick = {
                open = false
                pickFolder()?.let { f -> Settings.update { s -> if (f in s.videoFolders) s else s.copy(videoFolders = s.videoFolders + f) } }
            })
        }
    }
}

@Composable
private fun Thumb(v: LocalVideos.Video, modifier: Modifier) {
    val file by produceState<File?>(null, v.key, v.modified) { value = LocalVideos.thumbnail(v) }
    Box(modifier.aspectRatio(16 / 9f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        if (file == null) Icon(Icons.Rounded.Movie, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        else AsyncImage(file, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.High)
    }
}

@Composable
private fun VideoCard(v: LocalVideos.Video, point: Resume.Point?, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    val meta by produceState<LocalVideos.Meta?>(null, v.key, v.modified) { value = LocalVideos.meta(v) }
    HoverCard(modifier, onClick) {
        Box {
            Thumb(v, Modifier.fillMaxWidth())
            meta?.duration?.takeIf { it > 0 }?.let {
                Text(com.cappyjune.playa.desktop.ui.player.time(it), color = Color.White, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
            }
            if (point != null && point.duration > 0 && point.position < point.duration * 0.95) LinearProgressIndicator(
                progress = { (point.position / point.duration).coerceIn(0f, 1f) },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).height(4.dp), drawStopIndicator = {})
        }
        Text(v.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(listOfNotNull(meta?.let { m -> minOf(m.width, m.height).takeIf { it > 0 }?.let(::quality) }, size(v.size)).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FolderCard(f: LocalVideos.Folder, onClick: () -> Unit) {
    HoverCard(Modifier.fillMaxWidth(), onClick) {
        Box {
            Thumb(f.videos.first(), Modifier.fillMaxWidth())
            Icon(Icons.Rounded.Folder, null, Modifier.align(Alignment.TopStart).padding(8.dp).size(28.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp)).padding(4.dp), tint = Color.White)
        }
        Text(f.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text("${f.videos.size} video${if (f.videos.size == 1) "" else "s"} · ${f.path.replace(System.getProperty("user.home"), "~")}",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Quality by the short side, so a portrait phone clip reads 1080p, not 1920p. */
private fun quality(short: Int) = when { short >= 2000 -> "4K"; short >= 1400 -> "1440p"; short >= 1000 -> "1080p"; short >= 700 -> "720p"; short >= 560 -> "576p"; short >= 460 -> "480p"; else -> "${short}p" }
private fun size(b: Long) = if (b >= 1e9) "%.1f GB".format(b / 1e9) else "%.0f MB".format(b / 1e6)

/** The system's file dialog, several files at once. */
fun openFiles(): List<File>? =
    java.awt.FileDialog(null as java.awt.Frame?, "Play video files", java.awt.FileDialog.LOAD).apply { isMultipleMode = true; isVisible = true }
        .files.toList().takeIf { it.isNotEmpty() }

/** GNOME's own folder picker (zenity); Java's if that's missing. */
fun pickFolder(title: String = "Choose a folder of videos"): String? {
    runCatching {
        val p = ProcessBuilder("zenity", "--file-selection", "--directory", "--title=$title").start()
        val out = p.inputStream.bufferedReader().readText().trim()
        p.waitFor()
        return out.takeIf { p.exitValue() == 0 && File(it).isDirectory }
    }
    val c = javax.swing.JFileChooser().apply { fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY; dialogTitle = title }
    return if (c.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) c.selectedFile.path else null
}

