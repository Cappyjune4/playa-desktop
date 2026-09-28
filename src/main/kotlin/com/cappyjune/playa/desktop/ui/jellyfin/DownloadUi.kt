package com.cappyjune.playa.desktop.ui.jellyfin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cappyjune.playa.desktop.data.Downloads
import com.cappyjune.playa.desktop.data.JfItem

/** The sizes to download at, as a menu under whatever opened it. */
@Composable
fun QualityMenu(items: List<JfItem>, open: Boolean, onDismiss: () -> Unit) {
    val runtime = items.sumOf { it.runtimeMs }
    val original = items.sumOf { it.MediaSources?.firstOrNull()?.Size ?: 0L }.takeIf { it > 0 }
    DropdownMenu(open, onDismiss) {
        Text(if (items.size == 1) "Download for offline" else "Download ${items.size} episodes", Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Downloads.Quality.entries.forEach { q ->
            // An efficient original (HEVC, say) can be smaller than the server's own copy: say so.
            val est = q.estimate(runtime, original)
            val bigger = q != Downloads.Quality.ORIGINAL && est != null && original != null && est >= original
            DropdownMenuItem(onClick = { Downloads.enqueue(items, q); onDismiss() }, text = {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(q.label, style = MaterialTheme.typography.bodyLarge)
                    Text(listOfNotNull(
                        when { q == Downloads.Quality.ORIGINAL -> "The server's file, every audio and subtitle track"
                            bigger -> "Remade by the server"; else -> "Made smaller by the server" },
                        est?.let { "about ${size(it)}" + if (bigger) ", bigger than the original" else "" },
                    ).joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            })
        }
    }
}

/** A film or episode's download state as a button: download, queued, progress, done or failed. */
@Composable
fun DownloadButton(item: JfItem, tonal: Boolean = true, modifier: Modifier = Modifier) {
    val all by Downloads.items.collectAsState()
    val d = all[item.Id]
    var picking by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val content: @Composable () -> Unit = {
        when (d?.state) {
            null -> Icon(Icons.Rounded.Download, "Download")
            Downloads.State.QUEUED -> Icon(Icons.Rounded.Schedule, "Waiting to download")
            Downloads.State.DOWNLOADING -> CircularProgressIndicator(progress = { d.percent / 100f }, Modifier.size(26.dp), strokeWidth = 3.dp)
            Downloads.State.DONE -> Icon(Icons.Rounded.DownloadDone, "Downloaded", tint = MaterialTheme.colorScheme.primary)
            Downloads.State.FAILED -> Icon(Icons.Rounded.ErrorOutline, "Download failed: ${d.error}", tint = MaterialTheme.colorScheme.error)
        }
    }
    val onClick = {
        when (d?.state) {
            null -> picking = true
            Downloads.State.FAILED -> Downloads.retry(item.Id)
            else -> confirm = true
        }
    }
    Box(modifier) {
        if (tonal) FilledTonalIconButton(onClick = onClick, Modifier.size(48.dp)) { content() } else IconButton(onClick = onClick) { content() }
        QualityMenu(listOf(item), picking) { picking = false }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text(if (d?.done == true) "Remove the download?" else "Cancel the download?") },
        text = { Text(item.Name.orEmpty()) },
        icon = { Icon(Icons.Rounded.Delete, null) },
        confirmButton = { TextButton(onClick = { Downloads.delete(listOf(item.Id)); confirm = false }) { Text(if (d?.done == true) "Remove" else "Cancel download") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep") } })
}

fun size(b: Long) = when { b >= 1_000_000_000_000 -> "%.1f TB".format(b / 1e12); b >= 1_000_000_000 -> "%.1f GB".format(b / 1e9); b >= 1_000_000 -> "${b / 1_000_000} MB"; else -> "${b / 1000} KB" }

