package com.cappyjune.playa.desktop.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.cappyjune.playa.desktop.playback.Playback
import com.cappyjune.playa.desktop.playback.Scale
import com.cappyjune.playa.desktop.playback.Track
import kotlinx.coroutines.delay
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage

/** What the window can do for the player: fullscreen, and a file dialog for subtitles. */
class WindowControls(val fullscreen: Boolean, val toggleFullscreen: () -> Unit, val pickFile: (String) -> String?)
val LocalWindowControls = staticCompositionLocalOf { WindowControls(false, {}, { null }) }

/** The video, drawn where mpv put it: fitted and centred, or filling the area. */
@Composable
fun VideoSurface(modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Black).onSizeChanged { Playback.surfaceSize(it.width, it.height) }) {
        Canvas(Modifier.fillMaxSize()) {
            Playback.frameNo  // reading it here redraws on every new frame, without recomposing
            val bmp = Playback.frame ?: return@Canvas
            val s = minOf(size.width / bmp.width, size.height / bmp.height)
            val w = bmp.width * s; val h = bmp.height * s
            drawImage(bmp.asComposeImageBitmap(), srcSize = IntSize(bmp.width, bmp.height),
                dstOffset = IntOffset(((size.width - w) / 2).toInt(), ((size.height - h) / 2).toInt()), dstSize = IntSize(w.toInt(), h.toInt()))
        }
    }
}

/** An invisible cursor, for when the controls hide during playback. */
private val noCursor by lazy {
    PointerIcon(Toolkit.getDefaultToolkit().createCustomCursor(BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), Point(0, 0), "none"))
}

/**
 * The player, filling the window: the video, and Material controls over it that hide (with the
 * cursor) after a few still seconds. Click pauses, double-click is fullscreen, the wheel is volume.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlayerScreen() {
    val win = LocalWindowControls.current
    var lastMove by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var menuOpen by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }
    LaunchedEffect(lastMove, Playback.paused, menuOpen) {
        controls = true
        if (!Playback.paused && !menuOpen) { delay(2800); controls = false }
    }
    val poke = { lastMove = System.currentTimeMillis() }

    Box(Modifier.fillMaxSize().background(Color.Black)
        .onPointerEvent(PointerEventType.Move) { poke() }
        .onPointerEvent(PointerEventType.Scroll) { e ->
            val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
            Playback.changeVolume(Playback.volume - dy * 5); poke()
        }
        .then(if (controls) Modifier else Modifier.pointerHoverIcon(noCursor))
        // Clicks that no button took: on the whole area, so they work with the controls showing too.
        .pointerInput(win) { detectTapGestures(onTap = { Playback.togglePause(); poke() }, onDoubleTap = { win.toggleFullscreen() }) }
    ) {
        VideoSurface(Modifier.fillMaxSize())
        if (Playback.buffering && Playback.error == null) CircularProgressIndicator(Modifier.align(Alignment.Center).size(64.dp), color = Color.White)
        Playback.error?.let {
            Text(it, Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(14.dp)).padding(20.dp),
                color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        AnimatedVisibility(controls, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
            Controls(win, onMenu = { menuOpen = it; poke() })
        }
        // Above the seek bar, whether or not the controls are showing.
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 32.dp, bottom = 120.dp)) { SkipOrNext() }
    }
}

/**
 * Jellyfin's media segments: "Skip intro" (or recap, preview) while one's playing. Over the
 * credits, or in the last half-minute, an Up next card for the next episode instead.
 */
@Composable
private fun SkipOrNext() {
    val ms = (Playback.position * 1000).toLong()
    val live = Playback.current?.live == true
    val seg = Playback.segments.firstOrNull { it.Type in SKIPPABLE && ms >= it.startMs && ms < it.endMs - 1000 }
    val credits = seg?.Type == "Outro" || (Playback.duration > 60 && Playback.duration - Playback.position < 30)
    val next = Playback.nextTitle
    when {
        live || Playback.ended -> {}
        credits && Playback.hasNext && next != null -> Surface(shape = RoundedCornerShape(16.dp), color = Color.Black.copy(alpha = 0.8f), contentColor = Color.White) {
            Column(Modifier.padding(16.dp).widthIn(max = 320.dp)) {
                Text("Up next", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(next, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { Playback.next() }) { Icon(Icons.Rounded.SkipNext, null); Text(" Play now") }
                    if (seg != null) OutlinedButton(onClick = { Playback.seekTo(seg.endMs / 1000f) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Watch credits") }
                }
            }
        }
        seg != null && seg.Type != "Outro" -> Button(onClick = { Playback.seekTo(seg.endMs / 1000f) },
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
            Text(when (seg.Type) { "Intro" -> "Skip intro"; "Recap" -> "Skip recap"; "Preview" -> "Skip preview"; else -> "Skip" }, fontWeight = FontWeight.Bold)
            Icon(Icons.Rounded.SkipNext, null)
        }
        seg != null -> Button(onClick = { Playback.seekTo(seg.endMs / 1000f) },
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) { Text("Skip credits", fontWeight = FontWeight.Bold) }
    }
}

private val SKIPPABLE = setOf("Intro", "Recap", "Preview", "Outro")

@Composable
private fun Controls(win: WindowControls, onMenu: (Boolean) -> Unit) {
    val white = Color.White
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.7f), 0.2f to Color.Transparent,
        0.7f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f)))) {
        // Top: back, what's playing, and the menus.
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (win.fullscreen) win.toggleFullscreen(); Playback.stop() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close player", tint = white) }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(Playback.current?.title.orEmpty(), color = white, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Playback.current?.subtitle?.let { Text(it, color = white.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium, maxLines = 1) }
            }
            TracksMenu(win, onMenu)
            if (Playback.current?.live != true) SpeedMenu(onMenu)
            IconButton(onClick = { Playback.changeScale(Scale.entries[(Playback.scale.ordinal + 1) % Scale.entries.size]) }) {
                Icon(Icons.Rounded.AspectRatio, "Picture: ${Playback.scale.label}", tint = white)
            }
            IconButton(onClick = { if (win.fullscreen) win.toggleFullscreen(); Playback.mini = true }) { Icon(Icons.Rounded.PictureInPictureAlt, "Mini player", tint = white) }
            IconButton(onClick = win.toggleFullscreen) {
                Icon(if (win.fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, "Fullscreen", tint = white)
            }
        }

        // Middle: the transport, as on the phone (not when the file won't play: the message sits there).
        if (Playback.error == null) Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            val live = Playback.current?.live == true
            if (Playback.queue.size > 1) BigButton(Icons.Rounded.SkipPrevious, "Previous", 36) { Playback.previous() }
            if (!live) BigButton(Icons.Rounded.Replay10, "Back ${com.cappyjune.playa.desktop.data.Settings.now.skipBack}s", 40) { Playback.skipBack() }
            FilledIconButton(onClick = { Playback.togglePause() }, Modifier.size(84.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = white.copy(alpha = 0.18f), contentColor = white)) {
                Icon(when { Playback.ended -> Icons.Rounded.Replay; Playback.paused -> Icons.Rounded.PlayArrow; else -> Icons.Rounded.Pause },
                    if (Playback.paused) "Play" else "Pause", Modifier.size(52.dp))
            }
            if (!live) BigButton(Icons.Rounded.Forward10, "Forward ${com.cappyjune.playa.desktop.data.Settings.now.skipForward}s", 40) { Playback.skipForward() }
            if (Playback.queue.size > 1) BigButton(Icons.Rounded.SkipNext, "Next", 36, enabled = Playback.hasNext) { Playback.next() }
        }

        // Bottom: where you are, and the volume.
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            if (Playback.current?.live != true && Playback.duration > 0) SeekBar()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (Playback.current?.live == true) "Live" else "${time(Playback.position)} / ${time(Playback.duration)}",
                    color = white, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { Playback.toggleMute() }) {
                    Icon(if (Playback.muted || Playback.volume == 0f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, "Mute", tint = white)
                }
                Slider(if (Playback.muted) 0f else Playback.volume, { Playback.changeVolume(it) }, Modifier.width(140.dp), valueRange = 0f..150f,
                    colors = SliderDefaults.colors(thumbColor = white, activeTrackColor = white, inactiveTrackColor = white.copy(alpha = 0.3f)))
                Text("${Playback.volume.toInt()}%", color = white, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(44.dp))
            }
        }
    }
}

/** One frame from Jellyfin's trickplay sheets: a grid of thumbnails per image, this one cut out of it. */
@Composable
private fun TrickThumb(itemId: String, sourceId: String, t: com.cappyjune.playa.desktop.playback.Trick, seconds: Float, modifier: Modifier) {
    val n = ((seconds * 1000) / t.intervalMs).toInt().coerceIn(0, (t.count - 1).coerceAtLeast(0))
    val perSheet = t.tileWidth * t.tileHeight
    val cell = n % perSheet
    val url = com.cappyjune.playa.desktop.data.JfApi.trickplayTile(itemId, sourceId, t.width, n / perSheet)
    Column(modifier.wrapContentSize(unbounded = true, align = Alignment.TopStart), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.size(240.dp, 240.dp * t.height / t.width).clip(RoundedCornerShape(10.dp)).background(Color.Black)
            .border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(10.dp))) {
            val cw = maxWidth; val ch = maxHeight
            coil3.compose.AsyncImage(url, null, Modifier.wrapContentSize(unbounded = true, align = Alignment.TopStart)
                .requiredSize(cw * t.tileWidth, ch * t.tileHeight).offset(x = -cw * (cell % t.tileWidth), y = -ch * (cell / t.tileWidth)),
                contentScale = androidx.compose.ui.layout.ContentScale.FillBounds)
        }
        Text(time(seconds), Modifier.padding(top = 6.dp).background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
            color = Color.White)
    }
}

/** The seek bar: drag to scrub (the time and, from Jellyfin, a thumbnail follow), let go to jump there. */
@Composable
private fun SeekBar() {
    var dragging by remember { mutableStateOf(false) }
    var target by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints {
        val jf = Playback.current?.jf
        val trick = jf?.trick
        if (dragging && trick != null && jf.sourceId != null) {
            val w = 240.dp; val h = w * trick.height / trick.width
            val x = (maxWidth * (target / Playback.duration.coerceAtLeast(1f)) - w / 2).coerceIn(0.dp, (maxWidth - w).coerceAtLeast(0.dp))
            TrickThumb(jf.itemId, jf.sourceId, trick, target, Modifier.offset(x = x, y = -(h + 44.dp)))
        }
        Slider(if (dragging) target else Playback.position, { target = it; dragging = true },
            onValueChangeFinished = { Playback.seekTo(target); dragging = false },
            valueRange = 0f..Playback.duration.coerceAtLeast(1f),
            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f)))
        if (dragging && trick == null) Text(time(target), Modifier.align(Alignment.TopCenter).padding(bottom = 36.dp)
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp), color = Color.White)
    }
}

@Composable
private fun BigButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, size: Int, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, Modifier.size((size + 24).dp), enabled = enabled) {
        Icon(icon, label, Modifier.size(size.dp), tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f))
    }
}

/** Audio and subtitle tracks, plus loading a subtitle file from disk. */
@Composable
private fun TracksMenu(win: WindowControls, onMenu: (Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true; onMenu(true) }) { Icon(Icons.Rounded.ClosedCaption, "Audio and subtitles", tint = Color.White) }
        DropdownMenu(open, { open = false; onMenu(false) }) {
            val audio = Playback.tracks.filter { it.type == "audio" }
            val subs = Playback.tracks.filter { it.type == "sub" }
            MenuHeading("Audio")
            if (audio.isEmpty()) DropdownMenuItem({ Text("None") }, {}, enabled = false)
            audio.forEach { t -> TrackItem(t.label, t.selected) { Playback.selectTrack(t, "audio") } }
            HorizontalDivider()
            MenuHeading("Subtitles")
            TrackItem("Off", subs.none { it.selected }) { Playback.selectTrack(null, "sub") }
            subs.forEach { t -> TrackItem(t.label + if (t.external) " (file)" else "", t.selected) { Playback.selectTrack(t, "sub") } }
            DropdownMenuItem({ Text("Load subtitle file…") }, {
                open = false; onMenu(false)
                win.pickFile("Choose a subtitle file")?.let(Playback::addSubtitle)
            })
        }
    }
}

@Composable
private fun SpeedMenu(onMenu: (Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true; onMenu(true) }) { Icon(Icons.Rounded.Speed, "Speed", tint = Color.White) }
        DropdownMenu(open, { open = false; onMenu(false) }) {
            MenuHeading("Speed")
            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { s ->
                TrackItem(if (s == 1f) "Normal" else "${s}×", Playback.speed == s) { Playback.changeSpeed(s) }
            }
        }
    }
}

@Composable
private fun MenuHeading(t: String) = Text(t, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.primary)

@Composable
private fun TrackItem(label: String, on: Boolean, pick: () -> Unit) = DropdownMenuItem(
    text = { Text(label) }, onClick = pick,
    leadingIcon = { if (on) Icon(Icons.Rounded.Check, null) else Spacer(Modifier.size(24.dp)) },
)

fun time(s: Float): String {
    val t = s.toInt().coerceAtLeast(0)
    return if (t >= 3600) "%d:%02d:%02d".format(t / 3600, t / 60 % 60, t % 60) else "%d:%02d".format(t / 60, t % 60)
}

/** Keys while the player's up, as in VLC and mpv. Returns true when it used the key. */
fun playerKey(key: androidx.compose.ui.input.key.Key, shift: Boolean, win: WindowControls): Boolean {
    val k = androidx.compose.ui.input.key.Key
    when (key) {
        k.Spacebar, k.K -> Playback.togglePause()
        // Arrows nudge 5 seconds (the on-screen buttons do the bigger skips); Shift jumps a minute.
        k.DirectionLeft -> Playback.skip(if (shift) -60 else -5)
        k.DirectionRight -> Playback.skip(if (shift) 60 else 5)
        k.DirectionUp -> Playback.changeVolume(Playback.volume + 5)
        k.DirectionDown -> Playback.changeVolume(Playback.volume - 5)
        k.F -> win.toggleFullscreen()
        k.Escape -> if (win.fullscreen) win.toggleFullscreen() else Playback.stop()
        k.M -> Playback.toggleMute()
        k.N -> Playback.next()
        k.P -> Playback.previous()
        k.I -> { if (win.fullscreen) win.toggleFullscreen(); Playback.mini = true }
        k.LeftBracket -> Playback.changeSpeed((Playback.speed - 0.25f).coerceAtLeast(0.25f))
        k.RightBracket -> Playback.changeSpeed((Playback.speed + 0.25f).coerceAtMost(3f))
        k.S -> Playback.tracks.filter { it.type == "sub" }.let { subs ->
            val i = subs.indexOfFirst { it.selected }
            Playback.selectTrack(subs.getOrNull(i + 1), "sub")  // past the last one: off
        }
        k.A -> Playback.tracks.filter { it.type == "audio" }.let { a ->
            if (a.isNotEmpty()) Playback.selectTrack(a[(a.indexOfFirst { it.selected } + 1) % a.size], "audio")
        }
        else -> {
            // 0-9: jump to 0%, 10%... 90% of the way through.
            val digit = listOf(k.Zero, k.One, k.Two, k.Three, k.Four, k.Five, k.Six, k.Seven, k.Eight, k.Nine).indexOf(key)
            if (digit < 0 || Playback.duration <= 0) return false
            Playback.seekTo(Playback.duration * digit / 10f)
        }
    }
    return true
}
