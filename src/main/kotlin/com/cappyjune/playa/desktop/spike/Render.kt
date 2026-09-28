package com.cappyjune.playa.desktop.spike

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.cappyjune.playa.desktop.data.Jellyfin
import com.cappyjune.playa.desktop.ui.PlayaApp
import com.cappyjune.playa.desktop.ui.theme.PlayaTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * For checking the UI without a screen (Wayland doesn't let tools capture windows): draws the
 * app off-screen and saves a PNG. Usage: Render <out.png> [width] [height] [video file]
 */
fun main(args: Array<String>) {
    Jellyfin.init()
    com.cappyjune.playa.desktop.setupImages()
    // -Ppage=name:<title> opens the first search match's page.
    System.getProperty("playa.page")?.takeIf { it.startsWith("name:") }?.let { p ->
        val hit = kotlinx.coroutines.runBlocking { com.cappyjune.playa.desktop.data.JfApi.search(p.removePrefix("name:")) }.first()
        System.setProperty("playa.page", hit.Id)
    }
    val w = args.getOrNull(1)?.toInt() ?: 1400; val h = args.getOrNull(2)?.toInt() ?: 900
    // With a file: play it for a few seconds first, so the picture shows the player mid-play.
    args.getOrNull(3)?.let { f ->
        javax.swing.SwingUtilities.invokeAndWait { com.cappyjune.playa.desktop.playback.Playback.play(listOf(com.cappyjune.playa.desktop.fileMedia(File(f)))) }
        Thread.sleep(4000)
    }
    val none = object : androidx.compose.ui.draganddrop.DragAndDropTarget { override fun onDrop(event: androidx.compose.ui.draganddrop.DragAndDropEvent) = false }
    ImageComposeScene(w, h, Density(1f)) { PlayaTheme { PlayaApp(none) } }.use { scene ->
        // Let the first animations settle, and what comes from the server (and its artwork) arrive.
        val settle = System.getProperty("playa.settle")?.toInt() ?: 1
        repeat(settle * 4) { scene.render(it * 250_000_000L); Thread.sleep(250) }
        val img = scene.render(settle * 1_000_000_000L + 500_000_000L)
        File(args[0]).writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
    }
    kotlin.system.exitProcess(0)
}
