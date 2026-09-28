package com.cappyjune.playa.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.cappyjune.playa.desktop.data.Http
import com.cappyjune.playa.desktop.data.Paths
import com.cappyjune.playa.desktop.ui.jellyfin.JfBack
import okio.Path.Companion.toOkioPath
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.cappyjune.playa.desktop.data.Jellyfin
import com.cappyjune.playa.desktop.data.Settings
import com.cappyjune.playa.desktop.playback.Playback
import com.cappyjune.playa.desktop.ui.PlayaApp
import com.cappyjune.playa.desktop.ui.player.LocalWindowControls
import com.cappyjune.playa.desktop.ui.player.MiniPlayerWindow
import com.cappyjune.playa.desktop.ui.player.WindowControls
import com.cappyjune.playa.desktop.ui.player.playerKey
import com.cappyjune.playa.desktop.ui.theme.PlayaTheme
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import java.awt.Dimension
import java.awt.FileDialog
import java.awt.datatransfer.DataFlavor
import java.io.File

/**
 * Playa for Ubuntu: one resizable window that opens where it was last closed. Files given on the
 * command line (as "Open with" does) play straight away.
 */
@OptIn(FlowPreview::class, ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val files = args.map(::File).filter { it.isFile }
    // Already open: that window plays these (or just comes to the front).
    if (Instance.handOff(files.map { it.absolutePath })) return
    // The dock matches the window to playa.desktop by this (Java would call it "com-cappyjune-...").
    runCatching {
        val tk = java.awt.Toolkit.getDefaultToolkit()
        tk.javaClass.getDeclaredField("awtAppClassName").apply { isAccessible = true }.set(tk, "playa")
    }
    Jellyfin.init()
    setupImages()
    com.cappyjune.playa.desktop.data.Downloads.start()
    val last = Settings.now.window
    Instance.listen { paths -> paths.map(::File).filter { it.isFile }.takeIf { it.isNotEmpty() }?.let { f -> Playback.play(f.map(::fileMedia)) } }
    application {
        val state = rememberWindowState(
            size = DpSize((last?.width ?: 1400).dp, (last?.height ?: 900).dp),
            position = last?.let { WindowPosition(it.x.dp, it.y.dp) } ?: WindowPosition.PlatformDefault,
            placement = if (last?.maximized == true) WindowPlacement.Maximized else WindowPlacement.Floating,
        )
        // Fullscreen goes back to whatever it was before (floating or maximised).
        val before = remember { arrayOf(WindowPlacement.Floating) }
        val toggleFullscreen = {
            if (state.placement == WindowPlacement.Fullscreen) state.placement = before[0]
            else { before[0] = state.placement; state.placement = WindowPlacement.Fullscreen }
        }
        Window(onCloseRequest = { Playback.stop(); exitApplication() }, state = state, title = Playback.current?.title?.let { "$it · Playa" } ?: "Playa",
            icon = androidx.compose.ui.res.painterResource("icon.png"),
            onPreviewKeyEvent = { e ->
                e.type == KeyEventType.KeyDown && when {
                    Playback.showing && !Playback.mini ->
                        playerKey(e.key, e.isShiftPressed, WindowControls(state.placement == WindowPlacement.Fullscreen, toggleFullscreen, { null }))
                    // Escape goes back a page in Jellyfin.
                    e.key == Key.Escape -> JfBack.back()
                    else -> false
                }
            }) {
            window.minimumSize = Dimension(720, 520)
            LaunchedEffect(Instance.raise) {
                if (Instance.raise > 0) { if (state.isMinimized) state.isMinimized = false; window.toFront(); window.requestFocus() }
            }
            val pickFile: (String) -> String? = { title ->
                FileDialog(window, title, FileDialog.LOAD).apply { isVisible = true }.let { d -> d.file?.let { File(d.directory, it).path } }
            }
            // Drop video files anywhere on the window to play them (several make a queue).
            val drop = remember {
                object : DragAndDropTarget {
                    override fun onDrop(event: DragAndDropEvent): Boolean {
                        val t = event.awtTransferable
                        if (!t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return false
                        @Suppress("UNCHECKED_CAST") val dropped = (t.getTransferData(DataFlavor.javaFileListFlavor) as List<File>).filter { it.isFile }
                        if (dropped.isEmpty()) return false
                        Playback.play(dropped.map(::fileMedia)); return true
                    }
                }
            }
            CompositionLocalProvider(LocalWindowControls provides WindowControls(state.placement == WindowPlacement.Fullscreen, toggleFullscreen, pickFile)) {
                PlayaTheme { PlayaApp(drop) }
            }
        }
        if (Playback.showing && Playback.mini) MiniPlayerWindow()
        LaunchedEffect(Unit) { if (files.isNotEmpty()) Playback.play(files.map(::fileMedia)) }
        // Remember the window's place and size (a moment after it stops moving), not while fullscreen.
        LaunchedEffect(state) {
            snapshotFlow { Triple(state.position, state.size, state.placement) }.debounce(500).collect { (pos, size, placement) ->
                val p = pos as? WindowPosition.Absolute ?: return@collect
                if (placement == WindowPlacement.Fullscreen) return@collect
                Settings.update { s ->
                    s.copy(window = Settings.WindowBounds(p.x.value.toInt(), p.y.value.toInt(), size.width.value.toInt(), size.height.value.toInt(),
                        placement == WindowPlacement.Maximized))
                }
            }
        }
    }
}

/** Artwork through the app's own client (Jellyfin's token as a header), cached on disk. */
fun setupImages() = SingletonImageLoader.setSafe { ctx ->
    ImageLoader.Builder(ctx)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { Http.client })) }
        .diskCache { DiskCache.Builder().directory(Paths.cache.resolve("images").toOkioPath()).maxSizeBytes(512L * 1024 * 1024).build() }
        .build()
}

/** A file on this computer, as something to play (mpv finds subtitle files beside it itself). */
fun fileMedia(f: File) = com.cappyjune.playa.desktop.playback.Media(f.path, f.nameWithoutExtension, f.parentFile?.name)
