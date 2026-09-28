package com.cappyjune.playa.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.zIndex
import com.cappyjune.playa.desktop.ui.jellyfin.HomeScreen
import com.cappyjune.playa.desktop.ui.jellyfin.JfHost
import com.cappyjune.playa.desktop.ui.jellyfin.LibraryScreen
import com.cappyjune.playa.desktop.ui.jellyfin.Messages
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.draganddrop.DragAndDropTarget
import com.cappyjune.playa.desktop.data.Jellyfin
import com.cappyjune.playa.desktop.fileMedia
import com.cappyjune.playa.desktop.playback.Playback
import com.cappyjune.playa.desktop.ui.player.LocalWindowControls
import com.cappyjune.playa.desktop.ui.player.PlayerScreen

/** Playa's sections, in the menu's order; the Jellyfin ones sit under a Jellyfin heading. */
enum class Section(val label: String, val title: String, val icon: ImageVector, val jellyfin: Boolean = false) {
    HOME("Home", "Jellyfin", Icons.Rounded.Home, jellyfin = true),
    LIBRARY("Library", "Library", Icons.Rounded.VideoLibrary, jellyfin = true),
    /** Only for administrator accounts. */
    DASHBOARD("Dashboard", "Dashboard", Icons.Rounded.Dashboard, jellyfin = true),
    TV("Live TV", "Live TV", Icons.Rounded.LiveTv),
    LOCAL("Local", "On this computer", Icons.Rounded.Computer),
    OFFLINE("Offline", "Offline", Icons.Rounded.Download),
}

/**
 * Sign in to Jellyfin first; after that a permanent side menu (an icon rail when the window's
 * narrow) and the chosen section beside it. Settings is its own page from the bottom of the menu.
 */
@Composable
fun PlayaApp(drop: DragAndDropTarget) {
    val account by Jellyfin.account.collectAsState()
    // Video files dropped anywhere play (see Main).
    Surface(Modifier.fillMaxSize().dragAndDropTarget(shouldStartDragAndDrop = { true }, target = drop)) {
        // The player takes the whole window while it's up (the mini player leaves the app showing).
        if (Playback.showing && !Playback.mini) { PlayerScreen(); return@Surface }
        if (account == null) { SignInScreen(); return@Surface }
        // (The dev render picks a section and page with -Dplaya.section / -Dplaya.page.)
        var section by remember { mutableStateOf(System.getProperty("playa.section")?.let { Section.valueOf(it) } ?: Section.HOME) }
        var settings by remember { mutableStateOf(false) }
        // The Dashboard (and the rescan menus) only for an administrator's account.
        val admin by com.cappyjune.playa.desktop.data.JfAdmin.isAdmin.collectAsState()
        LaunchedEffect(account) { if (account != null) com.cappyjune.playa.desktop.data.JfAdmin.checkAdmin() }
        val sections = Section.entries.filter { it != Section.DASHBOARD || admin }
        val pick: (Section) -> Unit = { section = it; settings = false }

        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(Messages.current) { Messages.current?.let { snackbar.showSnackbar(it, withDismissAction = true); Messages.current = null } }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(24.dp).zIndex(1f))
            val wide = maxWidth >= 1000.dp
            Row(Modifier.fillMaxSize()) {
                if (wide) PermanentDrawerSheet(Modifier.width(248.dp)) {
                    Box(Modifier.padding(horizontal = 28.dp, vertical = 24.dp)) { PlayaLogo() }
                    sections.forEach { s ->
                        if (s == Section.HOME) Row(Modifier.padding(horizontal = 28.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            JellyfinLogo(18.dp); Text("  Jellyfin", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (s == Section.TV) HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 10.dp))
                        NavigationDrawerItem(label = { Text(s.label) }, selected = !settings && section == s, onClick = { pick(s) },
                            icon = { Icon(s.icon, null, tint = sectionColour(s)) }, modifier = Modifier.padding(horizontal = 12.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    NavigationDrawerItem(label = { Text("Settings") }, selected = settings, onClick = { settings = true },
                        icon = { Icon(Icons.Rounded.Settings, null) }, modifier = Modifier.padding(12.dp))
                } else NavigationRail(Modifier.fillMaxHeight(), header = { Box(Modifier.padding(vertical = 12.dp)) { PlayaLogo() } }) {
                    Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        sections.forEach { s ->
                            NavigationRailItem(selected = !settings && section == s, onClick = { pick(s) },
                                icon = { Icon(s.icon, s.label, tint = sectionColour(s)) }, label = { Text(s.label) })
                        }
                        Spacer(Modifier.weight(1f))
                        NavigationRailItem(selected = settings, onClick = { settings = true }, icon = { Icon(Icons.Rounded.Settings, "Settings") },
                            label = { Text("Settings") })
                        Spacer(Modifier.height(12.dp))
                    }
                }
                AnimatedContent(if (settings) null else section, Modifier.weight(1f).fillMaxHeight(),
                    transitionSpec = { fadeIn() togetherWith fadeOut() }) { s ->
                    when (s) {
                        null -> SettingsScreen()
                        Section.HOME -> JfHost { HomeScreen(it) }
                        Section.LIBRARY -> JfHost { LibraryScreen(it) }
                        Section.TV -> com.cappyjune.playa.desktop.ui.tv.LiveTvScreen()
                        Section.LOCAL -> com.cappyjune.playa.desktop.ui.local.LocalScreen()
                        Section.OFFLINE -> com.cappyjune.playa.desktop.ui.offline.OfflineScreen()
                        Section.DASHBOARD -> com.cappyjune.playa.desktop.ui.admin.DashboardScreen()
                    }
                }
            }
        }
    }
}
