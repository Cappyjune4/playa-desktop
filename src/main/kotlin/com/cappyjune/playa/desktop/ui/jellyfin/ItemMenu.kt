package com.cappyjune.playa.desktop.ui.jellyfin

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.DefaultContextMenuRepresentation
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FindReplace
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.cappyjune.playa.desktop.data.JfAdmin
import com.cappyjune.playa.desktop.data.JfItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * An administrator's server actions for an item: rescan it for new files, refresh its metadata,
 * or replace it all (after asking). The phone's hold menu; here a right-click, or the ⋮ button
 * on its page.
 */
private class AdminAction(val icon: ImageVector, val label: String, val detail: String, val confirm: Boolean = false, val run: () -> Unit)

private fun actions(item: JfItem, askReplace: () -> Unit): List<AdminAction> {
    val name = item.Name.orEmpty()
    // Seasons are usually virtual (no folder of their own) and an episode is one file, so a scan
    // there finds nothing new; new files are looked for in the whole show instead.
    val show = item.SeriesId?.takeIf { item.Type == "Season" || item.Type == "Episode" }
    val scanId = show ?: item.Id
    val scanName = if (show != null) item.SeriesName ?: "the show" else name
    return listOf(
        AdminAction(Icons.Rounded.FolderOpen, "Scan for new files", "Looks through $scanName for added, changed or removed files") {
            run("Scanning $scanName") { JfAdmin.refresh(scanId, full = false, replace = false) }
        },
        AdminAction(Icons.Rounded.Sync, "Refresh metadata", "Fills in missing details and artwork, and keeps what's there") {
            run("Refreshing $name") { JfAdmin.refresh(item.Id, full = true, replace = false) }
        },
        AdminAction(Icons.Rounded.FindReplace, "Replace all metadata…", "Fetches every detail again, over any edits (artwork is kept)", confirm = true, run = askReplace),
    )
}

/** Its own scope: the request carries on if the card scrolls away or the page changes. */
private fun run(done: String, block: suspend () -> Unit) {
    CoroutineScope(Dispatchers.Main).launch {
        runCatching { block() }.onSuccess { Messages.show(done) }.onFailure { Messages.show("Couldn't: ${it.message}") }
    }
}

@Composable
private fun ReplaceDialog(item: JfItem, onDismiss: () -> Unit) {
    val name = item.Name.orEmpty()
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Replace all metadata?") },
        text = { Text("Every detail of $name${if (item.Type in setOf("Series", "Season", "CollectionFolder", "Folder", "BoxSet")) " and everything in it" else ""} is fetched again, replacing any edits made on the server.") },
        confirmButton = { TextButton(onClick = { onDismiss(); run("Replacing $name's metadata") { JfAdmin.refresh(item.Id, full = true, replace = true) } }) { Text("Replace") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

/** Right-click on [content] gives an administrator the item's server actions; for anyone else it's just [content]. */
@Composable
fun ItemMenuArea(item: JfItem, content: @Composable () -> Unit) {
    val admin by JfAdmin.isAdmin.collectAsState()
    if (!admin) { content(); return }
    var replacing by remember { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme
    CompositionLocalProvider(LocalContextMenuRepresentation provides DefaultContextMenuRepresentation(c.surfaceContainerHigh, c.onSurface, c.primary.copy(alpha = 0.16f))) {
        ContextMenuArea(items = { actions(item) { replacing = true }.map { a -> ContextMenuItem(a.label, a.run) } }) { content() }
    }
    if (replacing) ReplaceDialog(item) { replacing = false }
}

/** The same actions as a ⋮ button, for an item's own page. */
@Composable
fun ItemMenuButton(item: JfItem, modifier: Modifier = Modifier) {
    val admin by JfAdmin.isAdmin.collectAsState()
    if (!admin) return
    var open by remember { mutableStateOf(false) }
    var replacing by remember { mutableStateOf(false) }
    Box(modifier) {
        FilledTonalIconButton(onClick = { open = true }, Modifier.size(48.dp)) { Icon(Icons.Rounded.MoreVert, "Server actions") }
        DropdownMenu(open, { open = false }) {
            actions(item) { replacing = true }.forEach { a ->
                DropdownMenuItem(text = { androidx.compose.foundation.layout.Column {
                    Text(a.label); Text(a.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } }, leadingIcon = { Icon(a.icon, null) }, onClick = { open = false; a.run() })
            }
        }
    }
    if (replacing) ReplaceDialog(item) { replacing = false }
}
