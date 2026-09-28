package com.cappyjune.playa.desktop.ui.jellyfin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cappyjune.playa.desktop.data.JfApi
import com.cappyjune.playa.desktop.data.JfItem
import com.cappyjune.playa.desktop.playback.Playback
import com.cappyjune.playa.desktop.ui.Section
import com.cappyjune.playa.desktop.ui.SectionTitle
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private data class HomeData(val resume: List<JfItem>, val nextUp: List<JfItem>, val views: List<JfItem>, val latest: List<Pair<JfItem, List<JfItem>>>)

/** Jellyfin's home: Continue watching, Next up, the libraries, and what's new in each. */
@Composable
fun HomeScreen(open: (JfPage) -> Unit) {
    val play = rememberJfPlay()
    var data by remember { mutableStateOf<HomeData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    // Closing the player: Continue watching and Next up have moved on.
    LaunchedEffect(reload, Playback.closed) {
        runCatching {
            coroutineScope {
                val resume = async { JfApi.resume() }
                val next = async { JfApi.nextUp() }
                val views = JfApi.views()
                val latest = views.filter { it.CollectionType in setOf("movies", "tvshows", "homevideos", "mixed", null) }
                    .map { v -> async { v to runCatching { JfApi.latest(v.Id) }.getOrDefault(emptyList()) } }
                HomeData(resume.await(), next.await(), views, latest.map { it.await() }.filter { it.second.isNotEmpty() })
            }
        }.onSuccess { data = it; error = null }.onFailure { if (data == null) error = it.message }
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader(null, { SectionTitle(Section.HOME) }) {
            IconButton(onClick = { reload++ }) { Icon(Icons.Rounded.Refresh, "Refresh") }
            IconButton(onClick = { open(JfPage.Search) }) { Icon(Icons.Rounded.Search, "Search") }
        }
        Box(Modifier.fillMaxSize()) {
            val d = data
            when {
                d == null && error != null -> Empty("Couldn't reach Jellyfin: $error\n\nAway from home, the VPN needs to be on.")
                d == null -> CircularProgressIndicator(Modifier.align(Alignment.TopCenter).padding(top = 48.dp))
                else -> {
                    val state = rememberLazyListState()
                    LazyColumn(state = state, contentPadding = PaddingValues(bottom = 32.dp)) {
                        if (d.resume.isNotEmpty()) carousel("Continue watching") {
                            items(d.resume, key = { it.Id }) { StillCard(it, onClick = { play(it, false) }) }
                        }
                        if (d.nextUp.isNotEmpty()) carousel("Next up") {
                            items(d.nextUp, key = { it.Id }) { StillCard(it, onClick = { play(it, false) }) }
                        }
                        carousel("My libraries") {
                            items(d.views, key = { it.Id }) { v -> LibraryTile(v) { open(JfPage.Folder(v.Id, v.Name.orEmpty(), v.CollectionType)) } }
                        }
                        d.latest.forEach { (view, items) ->
                            carousel("Latest ${view.Name}") {
                                items(items, key = { it.Id }) { PosterCard(it, onClick = { open(JfPage.Item(it.Id)) }) }
                            }
                        }
                        if (d.resume.isEmpty() && d.nextUp.isEmpty() && d.latest.isEmpty()) item { Empty("Nothing here yet. Add some films or shows to Jellyfin.") }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                }
            }
        }
    }
}

/** A library: its artwork (which usually has the name printed on it), and the name underneath. */
@Composable
fun LibraryTile(v: JfItem, onClick: () -> Unit) = ItemMenuArea(v) {
    HoverCard(Modifier.width(260.dp), onClick) {
        Picture(JfApi.backdrop(v, 600) ?: JfApi.poster(v, 600), Modifier.fillMaxWidth().aspectRatio(16 / 9f))
        Text(v.Name.orEmpty(), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
