package com.cappyjune.playa.desktop.ui.jellyfin

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.cappyjune.playa.desktop.data.JellyfinPlay
import com.cappyjune.playa.desktop.data.JfApi
import com.cappyjune.playa.desktop.data.JfItem
import com.cappyjune.playa.desktop.data.JfPerson
import com.cappyjune.playa.desktop.playback.Playback
import kotlinx.coroutines.launch

/** A short message at the bottom of the window (the desktop's toast); PlayaApp shows it. */
object Messages {
    var current by mutableStateOf<String?>(null)
    fun show(text: String) { current = text }
}

/** Starts playback of a Jellyfin item from anywhere; says so if the server won't. */
@Composable
fun rememberJfPlay(): (JfItem, Boolean) -> Unit {
    val scope = rememberCoroutineScope()
    return { item, fromStart ->
        scope.launch {
            runCatching { JellyfinPlay.queueFor(item, fromStart) }
                .onSuccess { Playback.play(it) }
                .onFailure { Messages.show("Couldn't play: ${it.message}") }
        }
    }
}

@Composable
fun Picture(url: String?, modifier: Modifier, fallback: ImageVector = Icons.Rounded.Movie, corner: Dp = 12.dp) {
    val placeholder = @Composable {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Icon(fallback, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Box(modifier.clip(RoundedCornerShape(corner))) {
        if (url == null) placeholder()
        else SubcomposeAsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, loading = { placeholder() }, error = { placeholder() },
            // High: artwork shrunk with the default filter goes jaggy (library tiles' lettering most of all).
            filterQuality = FilterQuality.High)
    }
}

/** A card that lifts a little under the mouse, with a hand cursor. */
@Composable
fun HoverCard(modifier: Modifier, onClick: () -> Unit, content: @Composable (hovered: Boolean) -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val s by animateFloatAsState(if (hovered) 1.04f else 1f, label = "lift")
    Column(modifier.scale(s).hoverable(source).clickable(source, indication = null, onClick = onClick)
        .pointerHoverIcon(PointerIcon.Hand)) { content(hovered) }
}

/** Watched tick, or how many episodes are left, in the corner of a poster. */
@Composable
private fun Badge(item: JfItem, modifier: Modifier) {
    val left = item.UserData?.UnplayedItemCount?.takeIf { it > 0 && item.Type == "Series" }
    when {
        left != null -> Text("$left", modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
            color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        item.played -> Icon(Icons.Rounded.Check, "Watched", modifier.size(22.dp).background(MaterialTheme.colorScheme.primary, CircleShape).padding(3.dp),
            tint = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
private fun Progress(item: JfItem, modifier: Modifier) {
    val pct = item.UserData?.PlayedPercentage ?: return
    if (item.played || pct <= 0) return
    LinearProgressIndicator(progress = { (pct / 100).toFloat().coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).height(4.dp), drawStopIndicator = {})
}

/** A play icon over the artwork while the mouse is on it. */
@Composable
private fun PlayHint(visible: Boolean, modifier: Modifier) {
    if (visible) Icon(Icons.Rounded.PlayArrow, null, modifier.size(44.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape).padding(8.dp), tint = Color.White)
}

/** 2:3 poster for films, shows and collections. */
@Composable
fun PosterCard(item: JfItem, onClick: () -> Unit, modifier: Modifier = Modifier.width(150.dp)) = ItemMenuArea(item) {
    HoverCard(modifier, onClick) {
        Box(Modifier.fillMaxWidth().aspectRatio(2 / 3f)) {
            Picture(JfApi.poster(item), Modifier.fillMaxSize())
            Badge(item, Modifier.align(Alignment.TopEnd).padding(6.dp))
            Progress(item, Modifier.align(Alignment.BottomCenter))
        }
        Text(item.Name.orEmpty(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp))
        Text(item.ProductionYear?.toString() ?: item.SeriesName.orEmpty(), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** 16:9 still for Continue watching and Next up: click plays; episode frames, or a film's backdrop. */
@Composable
fun StillCard(item: JfItem, onClick: () -> Unit, modifier: Modifier = Modifier.width(300.dp)) = ItemMenuArea(item) {
    HoverCard(modifier, onClick) { hovered ->
        Box(Modifier.fillMaxWidth().aspectRatio(16 / 9f)) {
            Picture(JfApi.still(item), Modifier.fillMaxSize())
            PlayHint(hovered, Modifier.align(Alignment.Center))
            Progress(item, Modifier.align(Alignment.BottomCenter))
        }
        Text(if (item.Type == "Episode") item.SeriesName.orEmpty() else item.Name.orEmpty(), style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(if (item.Type == "Episode") listOfNotNull(item.episodeCode, item.Name).joinToString(" · ") else item.ProductionYear?.toString().orEmpty(),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun PersonCard(p: JfPerson, onClick: () -> Unit) {
    HoverCard(Modifier.width(110.dp), onClick) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Picture(JfApi.person(p), Modifier.size(100.dp), fallback = Icons.Rounded.Person, corner = 50.dp)
            Text(p.Name.orEmpty(), style = MaterialTheme.typography.labelMedium, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp))
            p.Role?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * A titled row that scrolls sideways, as on Jellyfin's home. With a mouse: arrows at the ends
 * (while the pointer's over the row) page along it, Shift+wheel scrolls it, and so does its scrollbar.
 */
fun LazyListScope.carousel(title: String, content: LazyListScope.() -> Unit) {
    item(key = "h-$title") {
        Text(title, Modifier.padding(start = 40.dp, top = 24.dp, bottom = 10.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
    item(key = "r-$title") {
        val state = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val source = remember { MutableInteractionSource() }
        val hovered by source.collectIsHoveredAsState()
        val page = { dir: Int -> scope.launch { state.animateScrollBy(dir * state.layoutInfo.viewportSize.width * 0.8f) }; Unit }
        Column(Modifier.hoverable(source)) {
            Box {
                LazyRow(state = state, contentPadding = PaddingValues(horizontal = 40.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), content = content)
                if (hovered && state.canScrollBackward) RowArrow(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Back", Modifier.align(Alignment.CenterStart)) { page(-1) }
                if (hovered && state.canScrollForward) RowArrow(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "More", Modifier.align(Alignment.CenterEnd)) { page(1) }
            }
            HorizontalScrollbar(rememberScrollbarAdapter(state), Modifier.fillMaxWidth().padding(horizontal = 40.dp))
        }
    }
}

@Composable
private fun RowArrow(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier.padding(horizontal = 4.dp).size(48.dp).pointerHoverIcon(PointerIcon.Hand),
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f))) {
        Icon(icon, label, Modifier.size(32.dp))
    }
}

@Composable
fun Empty(text: String, modifier: Modifier = Modifier) = Text(text, modifier.padding(40.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun MetaDot(parts: List<String?>) {
    val s = parts.filterNot { it.isNullOrBlank() }.joinToString("  ·  ")
    if (s.isNotEmpty()) Text(s, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun Gap(h: Dp = 8.dp) = Spacer(Modifier.height(h))

