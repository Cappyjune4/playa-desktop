package com.cappyjune.playa.desktop.ui.tv

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Church
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.cappyjune.playa.desktop.data.Iptv
import com.cappyjune.playa.desktop.playback.Playback
import com.cappyjune.playa.desktop.ui.Section
import com.cappyjune.playa.desktop.ui.SectionTitle
import com.cappyjune.playa.desktop.ui.jellyfin.HeaderText
import com.cappyjune.playa.desktop.ui.jellyfin.HoverCard
import com.cappyjune.playa.desktop.ui.jellyfin.JfBack
import com.cappyjune.playa.desktop.ui.jellyfin.PageHeader
import java.util.Locale

private const val FAVOURITES = "\u0001favourites"
private const val RECENT = "\u0001recent"

/**
 * Hypnotix's shape, as on the phone: playlist → TV/Movies/Series → a grid of groups (flags for
 * countries) → the channels in one. The search box searches the whole playlist.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveTvScreen() {
    val custom by Iptv.custom.collectAsState()
    val favourites by Iptv.favourites.collectAsState()
    val recent by Iptv.recent.collectAsState()
    val playlists = remember(custom) { Iptv.playlists }
    var playlistId by rememberSaveable { mutableStateOf(Iptv.builtIn.first().id) }
    val playlist = playlists.firstOrNull { it.id == playlistId } ?: playlists.first()
    var section by rememberSaveable { mutableStateOf(Iptv.Section.TV) }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var channels by remember(playlist.id) { mutableStateOf<List<Iptv.Channel>?>(null) }
    var error by remember(playlist.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(playlist.id, reload) {
        if (reload > 0) channels = null
        runCatching { Iptv.channels(playlist, refresh = reload > 0) }.onSuccess { channels = it; error = null }.onFailure { error = it.message }
    }
    val searching = query.trim().length >= 2
    // Escape: out of a search, then out of a group.
    JfBack.handler = { when { query.isNotEmpty() -> { query = ""; true }; group != null -> { group = null; true }; else -> false } }

    fun play(list: List<Iptv.Channel>, i: Int) {
        Iptv.played(list[i])
        Playback.play(list.map { it.toMedia(playlist.name) }, i)
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader(if (group != null && !searching) ({ group = null }) else null,
            { if (group != null && !searching) HeaderText(groupLabel(group!!)) else SectionTitle(Section.TV) }) {
            OutlinedTextField(query, { query = it }, Modifier.width(320.dp), singleLine = true,
                placeholder = { Text("Search ${playlist.name}") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") } },
                shape = RoundedCornerShape(28.dp))
            if (playlist.url != null) IconButton(onClick = { reload++ }) { Icon(Icons.Rounded.Refresh, "Refresh playlist") }
        }
        if (group == null || searching) FlowRow(Modifier.padding(horizontal = 40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            playlists.forEach { p -> PlaylistChip(p, p.id == playlist.id, onPick = { playlistId = p.id; group = null }, onRemove = { Iptv.removePlaylist(p) }) }
            FilterChip(false, { adding = true }, label = { Text("Add") }, leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) })
        }
        Box(Modifier.fillMaxSize()) {
            val all = channels
            when {
                all == null && error != null -> Column(Modifier.padding(40.dp)) {
                    Text("Couldn't load ${playlist.name}: $error", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { reload++ }) { Text("Try again") }
                }
                all == null -> CircularProgressIndicator(Modifier.align(Alignment.TopCenter).padding(top = 48.dp))
                searching -> {
                    val q = query.trim()
                    val hits = remember(all, q) { all.filter { it.name.contains(q, ignoreCase = true) }.take(500) }
                    ChannelGrid(hits, favourites, onPlay = { play(hits, it) })
                }
                group != null -> {
                    val list = when (group) { FAVOURITES -> favourites; RECENT -> recent; else -> all.filter { it.group.ifBlank { "Other" } == group } }
                    ChannelGrid(list, favourites, onPlay = { play(list, it) })
                }
                else -> {
                    val present = Iptv.Section.entries.filter { s -> all.any { it.section == s } }
                    if (section !in present) section = present.firstOrNull() ?: Iptv.Section.TV
                    val groups = remember(all, section) { Iptv.groups(all, section) }
                    val state = rememberLazyGridState()
                    LazyVerticalGrid(GridCells.Adaptive(150.dp), state = state, contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 12.dp, bottom = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (present.size > 1) item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                present.forEach { s -> FilterChip(s == section, { section = s }, label = { Text("${s.label} (${all.count { it.section == s }})") }) }
                            }
                        }
                        if (section == Iptv.Section.TV && favourites.isNotEmpty()) item { GroupTile("Favourites", favourites.size, Icons.Rounded.Favorite, Color(0xFFE53935)) { group = FAVOURITES } }
                        if (section == Iptv.Section.TV && recent.isNotEmpty()) item { GroupTile("Recent", recent.size, Icons.Rounded.History, Color(0xFF546E7A)) { group = RECENT } }
                        items(groups, key = { it.first }) { (g, list) ->
                            val label = Iptv.label(g)
                            val code = countryCode(label)
                            GroupTile(label, list.size, if (code == null) topicIcon(label) else null, hashColour(label), country = code) { group = g }
                        }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                }
            }
        }
    }
    if (adding) AddPlaylistDialog(onDismiss = { adding = false }) { name, url -> Iptv.addPlaylist(name, url); adding = false }
}

private fun groupLabel(g: String) = when (g) { FAVOURITES -> "Favourites"; RECENT -> "Recent"; else -> Iptv.label(g) }

/** A playlist; one of your own has "Remove" on a right-click. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PlaylistChip(p: Iptv.Playlist, selected: Boolean, onPick: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    Box(Modifier.onPointerEvent(PointerEventType.Press) { if (p.custom && it.button == PointerButton.Secondary) menu = true }) {
        FilterChip(selected, onPick, label = { Text(p.name) }, trailingIcon = if (p.custom) ({ Icon(Icons.Rounded.Close, "Remove", Modifier.size(16.dp).clickable { confirm = true }) }) else null)
        DropdownMenu(menu, { menu = false }) { DropdownMenuItem(text = { Text("Remove ${p.name}") }, onClick = { menu = false; confirm = true }) }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Remove ${p.name}?") },
        confirmButton = { TextButton(onClick = { onRemove(); confirm = false }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}

@Composable
private fun AddPlaylistDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add an M3U playlist") }, text = {
        Column {
            OutlinedTextField(url, { url = it }, label = { Text("Playlist address (http…)") }, singleLine = true, modifier = Modifier.width(420.dp))
            OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, singleLine = true, modifier = Modifier.padding(top = 8.dp).width(420.dp))
        }
    }, confirmButton = { TextButton(enabled = url.trim().startsWith("http"), onClick = { onAdd(name.trim(), url.trim()) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

/** A group: a country's flag, or an icon for the topic on a colour that's stable per name. */
@Composable
private fun GroupTile(label: String, count: Int, icon: ImageVector?, colour: Color, country: String? = null, onClick: () -> Unit) {
    HoverCard(Modifier.fillMaxWidth(), onClick) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val bg = if (country != null) MaterialTheme.colorScheme.surfaceContainerHighest else colour.copy(alpha = 0.85f)
            Box(Modifier.fillMaxWidth().aspectRatio(1.3f).background(bg, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                if (country != null) Text(flag(country), fontSize = 56.sp)
                else Icon(icon ?: Icons.Rounded.Folder, null, tint = Color.White, modifier = Modifier.size(44.dp))
            }
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            Text("$count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Channels as rows in as many columns as the window fits; click plays (the rest of the list queues after it). */
@Composable
private fun ChannelGrid(list: List<Iptv.Channel>, favourites: List<Iptv.Channel>, onPlay: (Int) -> Unit) {
    if (list.isEmpty()) { Text("Nothing here.", Modifier.padding(40.dp), color = MaterialTheme.colorScheme.onSurfaceVariant); return }
    val favUrls = remember(favourites) { favourites.map { it.url }.toSet() }
    val state = rememberLazyGridState()
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(GridCells.Adaptive(340.dp), state = state, contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 12.dp, bottom = 32.dp)) {
            itemsIndexed(list, key = { i, c -> "$i-${c.url}" }) { i, c ->
                Row(Modifier.clip(RoundedCornerShape(12.dp)).clickable { onPlay(i) }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Logo(c.logo)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(c.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (c.group.isNotBlank()) Text(Iptv.label(c.group), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val fav = c.url in favUrls
                    IconButton(onClick = { Iptv.toggleFavourite(c) }) {
                        Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (fav) "Remove favourite" else "Favourite",
                            tint = if (fav) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

@Composable
private fun Logo(url: String) {
    val box = Modifier.size(width = 72.dp, height = 48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
    val fallback = @Composable { Box(box, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.LiveTv, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } }
    if (url.isBlank()) fallback()
    else SubcomposeAsyncImage(url, null, box.padding(4.dp), contentScale = ContentScale.Fit, loading = { fallback() }, error = { fallback() }, filterQuality = FilterQuality.High)
}

/** A two-letter country code as its flag emoji: each letter becomes a regional indicator symbol. */
private fun flag(code: String): String = code.uppercase().map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("")

/** Country names (and a few common short forms) to their two-letter code, for the flag. */
private val countries: Map<String, String> by lazy {
    Locale.getISOCountries().associateBy { norm(Locale.Builder().setRegion(it).build().getDisplayCountry(Locale.ENGLISH)) } +
        mapOf("usa" to "US", "uk" to "GB", "united states of america" to "US", "great britain" to "GB", "england" to "GB", "russia" to "RU",
            "south korea" to "KR", "korea" to "KR", "north korea" to "KP", "vietnam" to "VN", "iran" to "IR", "syria" to "SY", "taiwan" to "TW",
            "czech republic" to "CZ", "czechia" to "CZ", "turkey" to "TR", "turkiye" to "TR", "uae" to "AE", "bolivia" to "BO", "venezuela" to "VE",
            "laos" to "LA", "moldova" to "MD", "tanzania" to "TZ", "macedonia" to "MK", "kosovo" to "XK", "ivory coast" to "CI", "palestine" to "PS",
            "hong kong" to "HK", "macao" to "MO", "macau" to "MO", "myanmar" to "MM", "bonaire" to "BQ", "trinidad" to "TT",
            "democratic republic of the congo" to "CD", "dr congo" to "CD", "republic of the congo" to "CG", "saint vincent and the grenadines" to "VC")
}

/** Lower case, no accents, "&" as "and", "St." as "Saint": so "Bosnia & Herzegovina" and "Réunion" match the playlists' spellings. */
private fun norm(name: String): String = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
    .replace(Regex("\\p{Mn}+"), "").lowercase().replace("&", "and").replace(Regex("\\bst\\. "), "saint ")
    .replace(Regex("\\s+"), " ").trim()

/** "日本 / Japan" style labels try each part. */
private fun countryCode(label: String): String? = label.split('/').firstNotNullOfOrNull { countries[norm(it)] }

private fun topicIcon(label: String): ImageVector {
    val l = label.lowercase()
    return when {
        listOf("news", "noticias", "info").any { it in l } -> Icons.Rounded.Newspaper
        listOf("sport", "football", "soccer", "deport").any { it in l } -> Icons.Rounded.SportsSoccer
        listOf("music", "musica", "música", "radio").any { it in l } -> Icons.Rounded.MusicNote
        listOf("movie", "film", "cinema", "vod").any { it in l } -> Icons.Rounded.Movie
        listOf("kid", "child", "cartoon", "anim", "infantil").any { it in l } -> Icons.Rounded.ChildCare
        listOf("doc", "educat", "science", "learn").any { it in l } -> Icons.Rounded.School
        listOf("business", "finance", "shop").any { it in l } -> Icons.Rounded.Business
        listOf("weather").any { it in l } -> Icons.Rounded.Cloud
        listOf("food", "cook").any { it in l } -> Icons.Rounded.Restaurant
        listOf("relig", "church", "faith", "islam", "christ").any { it in l } -> Icons.Rounded.Church
        listOf("entertain", "comedy", "series", "general", "lifestyle").any { it in l } -> Icons.Rounded.TheaterComedy
        else -> Icons.Rounded.LiveTv
    }
}

/** A colour from the name, so each group keeps its colour between visits. */
private fun hashColour(s: String): Color {
    val palette = listOf(0xFF1E88E5, 0xFF43A047, 0xFFFB8C00, 0xFFE53935, 0xFF8E24AA, 0xFF00897B, 0xFF3949AB, 0xFFD81B60, 0xFF6D4C41, 0xFF546E7A, 0xFF7CB342, 0xFF00ACC1)
    return Color(palette[(s.hashCode() and 0x7fffffff) % palette.size])
}
