package com.cappyjune.playa.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cappyjune.playa.desktop.data.Jellyfin
import com.cappyjune.playa.desktop.data.Paths
import com.cappyjune.playa.desktop.data.Settings
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val s by Settings.state.collectAsState()
    val account by Jellyfin.account.collectAsState()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Settings, null, Modifier.size(30.dp)); Spacer(Modifier.width(12.dp))
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.widthIn(max = 760.dp).padding(top = 16.dp)) {
            Heading("Look")
            Chips("Theme", Settings.ThemeMode.entries, s.theme, { it.label }) { v -> Settings.update { it.copy(theme = v) } }
            Chips("Colours", Settings.Colours.entries, s.colours, { it.label }) { v -> Settings.update { it.copy(colours = v) } }
            Chips("Font", Settings.AppFont.entries, s.font, { it.label }) { v -> Settings.update { it.copy(font = v) } }
            Toggle("Pure black in dark mode", "For OLED screens", s.pureBlack) { v -> Settings.update { it.copy(pureBlack = v) } }
            HorizontalDivider(Modifier.padding(vertical = 8.dp)); Heading("Playback")
            Chips("Skip back", listOf(5, 10, 15, 30), s.skipBack, { "${it}s" }) { v -> Settings.update { it.copy(skipBack = v) } }
            Chips("Skip forward", listOf(5, 10, 15, 30), s.skipForward, { "${it}s" }) { v -> Settings.update { it.copy(skipForward = v) } }
            HorizontalDivider(Modifier.padding(vertical = 8.dp)); Heading("Jellyfin")
            ListItem(headlineContent = { Text(account?.let { "Signed in as ${it.userName}" } ?: "Not signed in") },
                supportingContent = { account?.let { Text(it.server) } },
                trailingContent = { if (account != null) TextButton(onClick = { scope.launch { Jellyfin.signOut() } }) { Text("Sign out") } },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            HorizontalDivider(Modifier.padding(vertical = 8.dp)); Heading("About")
            Text("Playa for Ubuntu · settings in ${Paths.config} · your Jellyfin sign-in is kept in GNOME's keyring. " +
                "Fonts: Google Sans Flex and NeverMind, SIL Open Font License.",
                Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Heading(t: String) = Text(t, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary)

@Composable
private fun Toggle(title: String, sub: String, on: Boolean, set: (Boolean) -> Unit) = ListItem(
    headlineContent = { Text(title) }, supportingContent = { Text(sub) },
    trailingContent = { Switch(on, set) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(title: String, options: List<T>, current: T, label: (T) -> String, set: (T) -> Unit) {
    ListItem(headlineContent = { Text(title) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent), supportingContent = {
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o -> FilterChip(o == current, { set(o) }, label = { Text(label(o)) }) }
        }
    })
}
