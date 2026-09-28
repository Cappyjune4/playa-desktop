package com.cappyjune.playa.desktop.ui.jellyfin

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.cappyjune.playa.desktop.data.JfApi
import com.cappyjune.playa.desktop.data.JfItem
import com.cappyjune.playa.desktop.ui.Section
import com.cappyjune.playa.desktop.ui.SectionTitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Posters on a desktop: a little bigger than the phone's, as many across as the window fits. */
private val POSTER = GridCells.Adaptive(160.dp)
private val GRID_PAD = PaddingValues(start = 40.dp, end = 40.dp, top = 8.dp, bottom = 32.dp)

private fun JfItem.page(): JfPage =
    if (Type == "Folder" || Type == "CollectionFolder") JfPage.Folder(Id, Name.orEmpty(), CollectionType) else JfPage.Item(Id)

/** The Library section: pick a library across the top, browse it below. */
@Composable
fun LibraryScreen(open: (JfPage) -> Unit) {
    var views by remember { mutableStateOf<List<JfItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { JfApi.views() }.onSuccess { v -> views = v; if (selected == null) selected = v.firstOrNull()?.Id }.onFailure { error = it.message }
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader(null, { SectionTitle(Section.LIBRARY) }) {
            IconButton(onClick = { open(JfPage.Search) }) { Icon(Icons.Rounded.Search, "Search") }
        }
        Box(Modifier.fillMaxSize()) {
            val v = views
            when {
                v == null && error != null -> Empty("Couldn't reach Jellyfin: $error")
                v == null -> CircularProgressIndicator(Modifier.align(Alignment.TopCenter).padding(top = 48.dp))
                v.isEmpty() -> Empty("No video libraries on this server.")
                else -> {
                    val view = v.firstOrNull { it.Id == selected } ?: v.first()
                    Column {
                        LazyRow(contentPadding = PaddingValues(horizontal = 40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(v, key = { it.Id }) { lib -> ItemMenuArea(lib) { FilterChip(lib.Id == view.Id, { selected = lib.Id }, label = { Text(lib.Name.orEmpty()) }) } }
                        }
                        key(view.Id) { LibraryGrid(view.Id, view.CollectionType, open) }
                    }
                }
            }
        }
    }
}

/** A library or folder as a poster grid, 100 at a time, with sort and filters. */
@Composable
fun LibraryGrid(parentId: String, collectionType: String?, open: (JfPage) -> Unit) {
    val state = rememberLazyGridState()
    var sort by rememberSaveable { mutableStateOf(JfApi.Sort.NAME) }
    var unwatched by rememberSaveable { mutableStateOf(false) }
    var favourites by rememberSaveable { mutableStateOf(false) }
    var genre by rememberSaveable { mutableStateOf<String?>(null) }
    var genres by remember { mutableStateOf<List<String>>(emptyList()) }
    val items = remember(sort, unwatched, favourites, genre) { mutableStateListOf<JfItem>() }
    var total by remember(sort, unwatched, favourites, genre) { mutableIntStateOf(-1) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val canFilter = collectionType in setOf("movies", "tvshows", "boxsets", "homevideos", "mixed", null)
    LaunchedEffect(parentId) { genres = runCatching { JfApi.genres(parentId) }.getOrDefault(emptyList()) }
    fun more() {
        if (loading || (total >= 0 && items.size >= total)) return
        loading = true
        scope.launch {
            runCatching { JfApi.browse(parentId, collectionType, items.size, sort, unwatched, favourites, genre) }
                .onSuccess { items.addAll(it.Items); total = it.TotalRecordCount; error = null }
                .onFailure { error = it.message }
            loading = false
        }
    }
    LaunchedEffect(sort, unwatched, favourites, genre) { more() }
    val nearEnd by remember { derivedStateOf { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) > items.size - 30 } }
    LaunchedEffect(nearEnd, items.size) { if (nearEnd) more() }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(POSTER, state = state, contentPadding = GRID_PAD,
            horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (canFilter) item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(unwatched, { unwatched = !unwatched }, label = { Text("Unwatched") })
                    FilterChip(favourites, { favourites = !favourites }, label = { Text("Favourites") })
                    if (genres.isNotEmpty()) Box {
                        var menu by remember { mutableStateOf(false) }
                        FilterChip(genre != null, { menu = true }, label = { Text(genre ?: "Genre") })
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("All genres") }, onClick = { genre = null; menu = false })
                            genres.forEach { g -> DropdownMenuItem(text = { Text(g) }, onClick = { genre = g; menu = false }) }
                        }
                    }
                    Box(Modifier.weight(1f))
                    if (total > 0) Text("$total", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    Box {
                        var menu by remember { mutableStateOf(false) }
                        TextButton(onClick = { menu = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, null); Text("  ${sort.label}") }
                        DropdownMenu(menu, { menu = false }) {
                            JfApi.Sort.entries.forEach { s -> DropdownMenuItem(text = { Text(s.label) }, onClick = { sort = s; menu = false },
                                leadingIcon = { if (s == sort) Icon(Icons.Rounded.Check, null) }) }
                        }
                    }
                }
            }
            posters(items, open)
            if (total == 0) item(span = { GridItemSpan(maxLineSpan) }) { Empty(if (unwatched || favourites || genre != null) "Nothing matches these filters." else "This library is empty.") }
            if (error != null && items.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Empty("Couldn't load: $error") }
            if (loading) item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        }
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

private fun LazyGridScope.posters(list: List<JfItem>, open: (JfPage) -> Unit) =
    items(list, key = { it.Id }) { i -> PosterCard(i, onClick = { open(i.page()) }, modifier = Modifier.fillMaxWidth()) }

@Composable
private fun PosterGrid(list: List<JfItem>, open: (JfPage) -> Unit) {
    val state = rememberLazyGridState()
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(POSTER, state = state, contentPadding = GRID_PAD,
            horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) { posters(list, open) }
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

@Composable
fun FolderScreen(id: String, name: String, collectionType: String?, open: (JfPage) -> Unit, back: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PageHeader(back, { HeaderText(name) })
        LibraryGrid(id, collectionType, open)
    }
}

@Composable
fun SearchScreen(open: (JfPage) -> Unit, back: () -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<JfItem>?>(emptyList()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(q) {
        if (q.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(350); results = null
        results = runCatching { JfApi.search(q.trim()) }.getOrDefault(emptyList())
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader(back, {
            OutlinedTextField(q, { q = it }, Modifier.widthIn(max = 640.dp).fillMaxWidth().focusRequester(focus), singleLine = true,
                placeholder = { Text("Films, shows, episodes") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Rounded.Close, "Clear") } },
                shape = RoundedCornerShape(28.dp), keyboardActions = KeyboardActions.Default)
        })
        Box(Modifier.fillMaxSize()) {
            val r = results
            when {
                r == null -> CircularProgressIndicator(Modifier.align(Alignment.TopCenter).padding(top = 48.dp))
                r.isEmpty() && q.trim().length >= 2 -> Empty("Nothing matches \"$q\".")
                else -> PosterGrid(r, open)
            }
        }
    }
}

/** Everything someone's been in that's on the server. */
@Composable
fun PersonScreen(id: String, name: String, open: (JfPage) -> Unit, back: () -> Unit) {
    var items by remember { mutableStateOf<List<JfItem>?>(null) }
    LaunchedEffect(id) { items = runCatching { JfApi.byPerson(id) }.getOrDefault(emptyList()) }
    Column(Modifier.fillMaxSize()) {
        PageHeader(back, { HeaderText(name) })
        Box(Modifier.fillMaxSize()) {
            val r = items
            when {
                r == null -> CircularProgressIndicator(Modifier.align(Alignment.TopCenter).padding(top = 48.dp))
                r.isEmpty() -> Empty("Nothing else with $name on this server.")
                else -> PosterGrid(r, open)
            }
        }
    }
}
