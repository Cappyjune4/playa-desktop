package com.cappyjune.playa.desktop.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cappyjune.playa.desktop.data.JfAdmin
import com.cappyjune.playa.desktop.data.JfApi
import com.cappyjune.playa.desktop.ui.Section
import com.cappyjune.playa.desktop.ui.SectionTitle
import com.cappyjune.playa.desktop.ui.jellyfin.Messages
import com.cappyjune.playa.desktop.ui.jellyfin.PageHeader
import com.cappyjune.playa.desktop.ui.jellyfin.Picture
import com.cappyjune.playa.desktop.ui.jellyfin.size
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

private data class Board(
    val system: JfAdmin.SystemInfo, val sessions: List<JfAdmin.Session>, val tasks: List<JfAdmin.Task>, val activity: List<JfAdmin.Activity>,
    val counts: JfAdmin.Counts?, val storage: JfAdmin.Storage?, val libraries: List<JfAdmin.VirtualFolder>, val users: List<JfAdmin.User>, val plugins: List<JfAdmin.Plugin>,
)

/**
 * Jellyfin's admin dashboard: who's watching and how, the server's jobs, and its libraries,
 * as cards in as many columns as the window fits. Live while it's showing (every 5 seconds).
 */
@Composable
fun DashboardScreen() {
    val scope = rememberCoroutineScope()
    var board by remember { mutableStateOf<Board?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var messaging by remember { mutableStateOf<JfAdmin.Session?>(null) }
    var confirmRestart by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            runCatching {
                coroutineScope {
                    val sys = async { JfAdmin.system() }; val ses = async { JfAdmin.sessions() }; val tasks = async { JfAdmin.tasks() }
                    val act = async { JfAdmin.activity() }; val counts = async { runCatching { JfAdmin.counts() }.getOrNull() }
                    // The slower-changing parts only every sixth refresh.
                    val slow = board?.takeIf { tick % 6 != 0 }
                    val storage = async { slow?.storage ?: JfAdmin.storage() }
                    val libs = async { slow?.libraries ?: runCatching { JfAdmin.libraries() }.getOrDefault(emptyList()) }
                    val users = async { slow?.users ?: runCatching { JfAdmin.users() }.getOrDefault(emptyList()) }
                    val plugins = async { slow?.plugins ?: runCatching { JfAdmin.plugins() }.getOrDefault(emptyList()) }
                    Board(sys.await(), ses.await(), tasks.await(), act.await(), counts.await(), storage.await(), libs.await(), users.await(), plugins.await())
                }
            }.onSuccess { board = it; error = null }.onFailure { error = it.message }
            tick++
            delay(5_000)
        }
    }
    fun act(label: String, block: suspend () -> Unit) = scope.launch {
        runCatching { block() }.onSuccess { Messages.show(label) }.onFailure { Messages.show("Couldn't: ${it.message}") }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader(null, { SectionTitle(Section.DASHBOARD) })
        val b = board
        Box(Modifier.fillMaxSize()) {
            when {
                b == null && error != null -> Text("Couldn't reach the server: $error", Modifier.padding(40.dp))
                b == null -> CircularProgressIndicator(Modifier.align(Alignment.TopCenter).padding(top = 48.dp))
                else -> LazyVerticalStaggeredGrid(StaggeredGridCells.Adaptive(440.dp), state = rememberLazyStaggeredGridState(),
                    contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 8.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp), verticalItemSpacing = 20.dp) {
                    item(key = "server", span = StaggeredGridItemSpan.FullLine) {
                        ServerCard(b.system, b.counts, onScan = { act("Scanning all libraries") { JfAdmin.scanAll() } }, onRestart = { confirmRestart = true })
                    }
                    val playing = b.sessions.filter { it.NowPlayingItem != null }
                    playing.forEach { s ->
                        item(key = "play-${s.Id}") {
                            PlayingCard(s,
                                onToggle = { act(if (s.PlayState?.IsPaused == true) "Resumed" else "Paused") { JfAdmin.command(s.Id, if (s.PlayState?.IsPaused == true) "Unpause" else "Pause") } },
                                onStop = { act("Stopped") { JfAdmin.command(s.Id, "Stop") } },
                                onMessage = { messaging = s })
                        }
                    }
                    if (playing.isEmpty()) item(key = "idle-note") { Panel("Now playing") { Text("Nothing playing right now.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                    val idle = b.sessions.filter { it.NowPlayingItem == null }
                    if (idle.isNotEmpty()) item(key = "devices") {
                        Panel("Active devices") {
                            idle.forEach { s -> Row2(s.DeviceName ?: "Device", listOfNotNull(s.UserName, listOfNotNull(s.Client, s.ApplicationVersion).joinToString(" ").ifBlank { null }, ago(s.LastActivityDate)).joinToString(" · ")) }
                        }
                    }
                    if (b.libraries.isNotEmpty()) item(key = "libraries") {
                        Panel("Libraries") {
                            b.libraries.forEach { l ->
                                val folders = b.storage?.Libraries?.firstOrNull { it.Id == l.ItemId || it.Name == l.Name }?.Folders.orEmpty()
                                LibraryRow(l, folders) { l.ItemId?.let { id -> act("Scanning ${l.Name}") { JfAdmin.scan(id) } } }
                            }
                        }
                    }
                    item(key = "tasks") {
                        Panel("Scheduled tasks") {
                            b.tasks.sortedWith(compareBy({ it.State != "Running" }, { it.Category }, { it.Name })).forEach { t ->
                                TaskRow(t, onRun = { act("Started ${t.Name}") { JfAdmin.runTask(t.Id) } }, onStop = { act("Stopping ${t.Name}") { JfAdmin.stopTask(t.Id) } })
                            }
                        }
                    }
                    item(key = "activity") { Panel("Activity") { b.activity.forEach { ActivityRow(it) } } }
                    if (b.users.isNotEmpty()) item(key = "users") {
                        Panel("Users") {
                            b.users.forEach { u ->
                                Row2(u.Name.orEmpty() + if (u.Policy?.IsAdministrator == true) "  · admin" else "",
                                    if (u.Policy?.IsDisabled == true) "Disabled" else "Last active ${ago(u.LastActivityDate) ?: "never"}")
                            }
                        }
                    }
                    if (b.plugins.isNotEmpty()) item(key = "plugins") {
                        Panel("Plugins") { b.plugins.forEach { p -> Row2(p.Name.orEmpty(), listOfNotNull(p.Version, p.Status).joinToString(" · ")) } }
                    }
                }
            }
        }
    }
    messaging?.let { s -> MessageDialog(s, onDismiss = { messaging = null }) { text -> act("Message sent") { JfAdmin.message(s.Id, text) }; messaging = null } }
    if (confirmRestart) AlertDialog(onDismissRequest = { confirmRestart = false }, title = { Text("Restart Jellyfin?") },
        text = { Text("Anyone watching will be interrupted for a minute or so.") },
        confirmButton = { TextButton(onClick = { confirmRestart = false; act("Restarting the server") { JfAdmin.restart() } }) { Text("Restart") } },
        dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("Cancel") } })
}

@Composable
private fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun Row2(title: String, sub: String) = Column(Modifier.fillMaxWidth()) {
    Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServerCard(s: JfAdmin.SystemInfo, c: JfAdmin.Counts?, onScan: () -> Unit, onRestart: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.ServerName.orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(listOfNotNull("Jellyfin ${s.Version}", s.OperatingSystemDisplayName, s.SystemArchitecture).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (s.HasUpdateAvailable) Flag(Icons.Rounded.Info, "An update is available", MaterialTheme.colorScheme.primary)
                if (s.HasPendingRestart) Flag(Icons.Rounded.Warning, "Restart needed to finish applying changes", Color(0xFFFFB300))
                c?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(listOfNotNull(
                        it.MovieCount.takeIf { n -> n > 0 }?.let { n -> "$n films" }, it.SeriesCount.takeIf { n -> n > 0 }?.let { n -> "$n shows" },
                        it.EpisodeCount.takeIf { n -> n > 0 }?.let { n -> "$n episodes" }, it.SongCount.takeIf { n -> n > 0 }?.let { n -> "$n songs" },
                    ).joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Blue for the everyday action, red for the one that interrupts everyone.
                FilledTonalButton(onClick = onScan, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF1E88E5), contentColor = Color.White)) {
                    Icon(Icons.Rounded.Sync, null); Text(" Scan all libraries")
                }
                if (s.CanSelfRestart) FilledTonalButton(onClick = onRestart, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFE53935), contentColor = Color.White)) {
                    Icon(Icons.Rounded.RestartAlt, null); Text(" Restart")
                }
            }
        }
    }
}

@Composable
private fun Flag(icon: ImageVector, text: String, tint: Color) = Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, null, Modifier.size(16.dp), tint = tint); Text("  $text", style = MaterialTheme.typography.bodySmall, color = tint)
}

@Composable
private fun PlayingCard(s: JfAdmin.Session, onToggle: () -> Unit, onStop: () -> Unit, onMessage: () -> Unit) {
    val item = s.NowPlayingItem!!
    val pos = (s.PlayState?.PositionTicks ?: 0) / 10_000
    val dur = item.runtimeMs
    val t = s.TranscodingInfo
    val method = s.PlayState?.PlayMethod ?: if (t != null) "Transcode" else "DirectPlay"
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.padding(16.dp)) {
            Picture(JfApi.still(item, 400) ?: JfApi.poster(item), Modifier.width(160.dp).aspectRatio(16 / 9f))
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(if (item.Type == "Episode") listOfNotNull(item.SeriesName, item.episodeCode).joinToString(" · ") else item.Name.orEmpty(),
                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.Type == "Episode") Text(item.Name.orEmpty(), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(s.UserName, s.DeviceName, s.Client).joinToString(" · "), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // How it's being played, which is what the dashboard is mostly for.
                val colour = when (method) { "DirectPlay" -> Color(0xFF43A047); "DirectStream" -> Color(0xFF1E88E5); else -> Color(0xFFFB8C00) }
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(colour, CircleShape))
                    Text("  " + when (method) { "DirectPlay" -> "Direct play"; "DirectStream" -> "Direct stream"; else -> "Transcoding" },
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (dur > 0) Column(Modifier.padding(horizontal = 16.dp)) {
            LinearProgressIndicator(progress = { (pos.toFloat() / dur).coerceIn(0f, 1f) }, Modifier.fillMaxWidth(), drawStopIndicator = {})
            Text("${if (s.PlayState?.IsPaused == true) "Paused · " else ""}${fmt(pos)} / ${fmt(dur)}", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (t != null && method == "Transcode") Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text(listOfNotNull(
                listOfNotNull(t.Width?.let { w -> "${w}×${t.Height}" }, if (t.IsVideoDirect) "video copied" else t.VideoCodec?.uppercase()).joinToString(" "),
                if (t.IsAudioDirect) "audio copied" else listOfNotNull(t.AudioCodec?.uppercase(), t.AudioChannels?.let { "${it}ch" }).joinToString(" "),
                t.Framerate?.let { "%.0f fps".format(it) }, t.Bitrate?.let { "%.1f Mbps".format(it / 1e6) },
                t.HardwareAccelerationType?.takeIf { it != "none" }?.let { "HW: $it" },
            ).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            t.TranscodeReasons?.takeIf { it.isNotEmpty() }?.let { r ->
                Text("Because: " + r.joinToString { reason -> reason.replace(Regex("([a-z])([A-Z])"), "$1 $2").lowercase() },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.padding(start = 6.dp, bottom = 6.dp)) {
            if (s.SupportsRemoteControl) {
                IconButton(onClick = onToggle) { Icon(if (s.PlayState?.IsPaused == true) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, "Pause or resume") }
                IconButton(onClick = onStop) { Icon(Icons.Rounded.Stop, "Stop") }
            }
            IconButton(onClick = onMessage) { Icon(Icons.AutoMirrored.Rounded.Message, "Send a message") }
        }
    }
}

@Composable
private fun LibraryRow(l: JfAdmin.VirtualFolder, folders: List<JfAdmin.Folder>, onScan: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(l.Name.orEmpty(), style = MaterialTheme.typography.bodyLarge)
            val f = folders.firstOrNull { it.FreeSpace >= 0 }
            val scanning = l.RefreshStatus == "Active" || (l.RefreshProgress ?: 0.0) > 0
            Text(when {
                scanning -> "Scanning… ${l.RefreshProgress?.let { "%.0f%%".format(it) } ?: ""}"
                f != null -> "${size(f.FreeSpace)} free of ${size(f.FreeSpace + f.UsedSpace)}"
                else -> l.CollectionType ?: "Mixed"
            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (f != null && f.FreeSpace + f.UsedSpace > 0) LinearProgressIndicator(progress = { f.UsedSpace.toFloat() / (f.FreeSpace + f.UsedSpace) },
                Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp), drawStopIndicator = {})
        }
        IconButton(onClick = onScan) { Icon(Icons.Rounded.Sync, "Scan ${l.Name}") }
    }
}

@Composable
private fun TaskRow(t: JfAdmin.Task, onRun: () -> Unit, onStop: () -> Unit) {
    val running = t.State == "Running"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(t.Name.orEmpty(), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (running) {
                LinearProgressIndicator(progress = { ((t.CurrentProgressPercentage ?: 0.0) / 100).toFloat() }, Modifier.fillMaxWidth().padding(vertical = 4.dp), drawStopIndicator = {})
                Text("Running ${t.CurrentProgressPercentage?.let { "%.0f%%".format(it) } ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            } else {
                val r = t.LastExecutionResult
                Text(listOfNotNull(t.Category, r?.let { "${if (it.Status == "Completed") "Last ran" else it.Status} ${ago(it.EndTimeUtc) ?: ""}".trim() } ?: "Never run").joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = if (r?.Status == "Failed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (running) IconButton(onClick = onStop) { Icon(Icons.Rounded.Stop, "Stop ${t.Name}") }
        else IconButton(onClick = onRun) { Icon(Icons.Rounded.PlayArrow, "Run ${t.Name}") }
    }
}

@Composable
private fun ActivityRow(e: JfAdmin.Activity) {
    val (icon, tint) = when (e.Severity) {
        "Error", "Critical" -> Icons.Rounded.Error to MaterialTheme.colorScheme.error
        "Warning" -> Icons.Rounded.Warning to Color(0xFFFFB300)
        else -> Icons.Rounded.CheckCircle to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(18.dp).padding(top = 2.dp), tint = tint)
        Column(Modifier.padding(start = 10.dp)) {
            Text(e.Name.orEmpty(), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(e.ShortOverview?.takeIf { it.isNotBlank() }, ago(e.Date)).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MessageDialog(s: JfAdmin.Session, onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Message ${s.UserName ?: "them"}") },
        text = { Column { Text("Shows on ${s.DeviceName ?: "their screen"} for 10 seconds.", style = MaterialTheme.typography.bodySmall); OutlinedTextField(text, { text = it }, Modifier.padding(top = 8.dp)) } },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onSend(text.trim()) }) { Text("Send") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

private fun fmt(ms: Long): String { val s = ms / 1000; return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }

/** "3m ago", "2h ago", "5d ago" from an ISO timestamp. */
private fun ago(iso: String?): String? {
    val t = iso?.let { runCatching { Instant.parse(it) }.getOrNull() ?: runCatching { Instant.parse(it.substringBefore('.') + "Z") }.getOrNull() } ?: return null
    val d = Duration.between(t, Instant.now())
    return when { d.toMinutes() < 1 -> "just now"; d.toMinutes() < 60 -> "${d.toMinutes()}m ago"; d.toHours() < 24 -> "${d.toHours()}h ago"; else -> "${d.toDays()}d ago" }
}
