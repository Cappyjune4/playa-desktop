package com.cappyjune.playa.desktop.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.rememberWindowState
import com.cappyjune.playa.desktop.playback.Playback
import com.cappyjune.playa.desktop.ui.theme.PlayaTheme
import java.awt.GraphicsEnvironment

/**
 * Picture-in-picture: the video in a small borderless window that stays on top of everything.
 * Drag it anywhere; hovering shows play/pause, skips, back to the big player, and close.
 */
@Composable
fun MiniPlayerWindow() {
    val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    val w = 480; val h = 270
    val state = rememberWindowState(size = DpSize(w.dp, h.dp), position = WindowPosition((screen.x + screen.width - w - 32).dp, (screen.y + screen.height - h - 32).dp))
    Window(onCloseRequest = { Playback.stop() }, state = state, title = "Playa", undecorated = true, alwaysOnTop = true, resizable = true,
        icon = androidx.compose.ui.res.painterResource("icon.png")) {
        PlayaTheme(forceDark = true) { MiniContent() }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WindowScope.MiniContent() {
    var hover by remember { mutableStateOf(false) }
    // The whole window is the handle for dragging it around.
    WindowDraggableArea(Modifier.fillMaxSize().onPointerEvent(PointerEventType.Enter) { hover = true }.onPointerEvent(PointerEventType.Exit) { hover = false }) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VideoSurface(Modifier.fillMaxSize())
            AnimatedVisibility(hover, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
                    Row(Modifier.align(Alignment.TopEnd)) {
                        IconButton(onClick = { Playback.mini = false }) { Icon(Icons.Rounded.OpenInFull, "Back to the big player", tint = Color.White) }
                        IconButton(onClick = { Playback.stop() }) { Icon(Icons.Rounded.Close, "Close", tint = Color.White) }
                    }
                    Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                        if (Playback.current?.live != true) IconButton(onClick = { Playback.skipBack() }) { Icon(Icons.Rounded.Replay10, "Back", tint = Color.White) }
                        IconButton(onClick = { Playback.togglePause() }, Modifier.size(64.dp)) {
                            Icon(if (Playback.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, "Play/pause", Modifier.size(44.dp), tint = Color.White)
                        }
                        if (Playback.current?.live != true) IconButton(onClick = { Playback.skipForward() }) { Icon(Icons.Rounded.Forward10, "Forward", tint = Color.White) }
                    }
                    if (Playback.duration > 0) LinearProgressIndicator(progress = { Playback.position / Playback.duration },
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp), drawStopIndicator = {})
                }
            }
        }
    }
}
