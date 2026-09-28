package com.cappyjune.playa.desktop.spike

import com.cappyjune.playa.desktop.playback.MpvPlayer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import org.jetbrains.skia.Bitmap
import java.io.File
import kotlin.system.exitProcess

/** What the window shows; written by the player and the poller, read by Compose. */
object Ui {
    var frame by mutableStateOf<Bitmap?>(null)
    var frameNo by mutableIntStateOf(0)
    var title by mutableStateOf("")
    var position by mutableFloatStateOf(0f)
    var duration by mutableFloatStateOf(1f)
    var paused by mutableStateOf(false)
    var stats by mutableStateOf("")
}

/**
 * The playback spike: does mpv inside a Compose window, with Material controls over it, run
 * smoothly? Usage: playa-desktop <file> [--seconds=N] [--snapshot=out.png]
 */
fun main(args: Array<String>) {
    val file = args.firstOrNull { !it.startsWith("--") } ?: error("Give a video file")
    val seconds = args.firstOrNull { it.startsWith("--seconds=") }?.substringAfter("=")?.toInt()
    val snapshot = args.firstOrNull { it.startsWith("--snapshot=") }?.substringAfter("=")
    val (ww, wh) = (args.firstOrNull { it.startsWith("--window=") }?.substringAfter("=") ?: "1280x760").split("x").map { it.toInt() }
    val player = MpvPlayer()
    player.onFrame = { bmp -> Ui.frame = bmp; Ui.frameNo++ }
    player.load(file)
    Ui.title = File(file).name

    application {
        Window(onCloseRequest = { player.close(); exitApplication() }, title = "Playa spike", state = rememberWindowState(width = ww.dp, height = wh.dp)) {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF4DA6F5))) {
                PlayerScreen(player)
            }
        }
        LaunchedEffect(Unit) { poll(player, seconds, snapshot) }
    }
}

@Composable
fun PlayerScreen(player: MpvPlayer) {
    Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { size ->
        // Draw at the window's own pixel size (fitted to the video's shape), no bigger than the video.
        val vw = player.get("dwidth")?.toIntOrNull() ?: 1920; val vh = player.get("dheight")?.toIntOrNull() ?: 1080
        val scale = minOf(size.width.toFloat() / vw, size.height.toFloat() / vh, 1f)
        player.targetWidth = (vw * scale).toInt(); player.targetHeight = (vh * scale).toInt()
    }) {
        Canvas(Modifier.fillMaxSize()) {
            Ui.frameNo  // redraw on every new frame
            val bmp = Ui.frame ?: return@Canvas
            val s = minOf(size.width / bmp.width, size.height / bmp.height)
            val w = bmp.width * s; val h = bmp.height * s
            drawImage(bmp.asComposeImageBitmap(), srcSize = IntSize(bmp.width, bmp.height),
                dstOffset = IntOffset(((size.width - w) / 2).toInt(), ((size.height - h) / 2).toInt()), dstSize = IntSize(w.toInt(), h.toInt()))
        }
        // Material controls, drawn over the video like any other layer.
        Text(Ui.title, Modifier.align(Alignment.TopStart).padding(20.dp), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(Ui.stats, Modifier.align(Alignment.TopEnd).padding(16.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).padding(10.dp),
            color = Color.White, style = MaterialTheme.typography.labelMedium)
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))).padding(16.dp)) {
            Slider(Ui.position, { player.command("seek", it.toString(), "absolute"); Ui.position = it }, valueRange = 0f..Ui.duration.coerceAtLeast(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(onClick = { player.command("cycle", "pause") }) {
                    Icon(if (Ui.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, "Play/pause")
                }
                Spacer(Modifier.width(12.dp))
                Text("${fmt(Ui.position)} / ${fmt(Ui.duration)}", color = Color.White)
            }
        }
    }
}

private fun fmt(s: Float) = "%d:%02d".format(s.toInt() / 60, s.toInt() % 60)

/** Every second: position, and the measurements (frames drawn, time per frame, CPU, decoder). */
private suspend fun poll(player: MpvPlayer, seconds: Int?, snapshot: String?) {
    val ticksPerSec = 100.0
    fun cpuTicks() = File("/proc/self/stat").readText().substringAfterLast(')').trim().split(" ").let { it[11].toLong() + it[12].toLong() }
    var lastFrames = 0L; var lastNanos = 0L; var lastTicks = cpuTicks(); var lastT = System.nanoTime()
    val start = System.nanoTime(); val samples = mutableListOf<String>()
    while (true) {
        delay(1000)
        Ui.position = player.get("time-pos")?.toFloatOrNull() ?: Ui.position
        Ui.duration = player.get("duration")?.toFloatOrNull() ?: Ui.duration
        Ui.paused = player.get("pause") == "yes"
        val now = System.nanoTime(); val secs = (now - lastT) / 1e9
        val frames = player.framesRendered - lastFrames
        val ms = if (frames > 0) (player.renderNanos - lastNanos) / 1e6 / frames else 0.0
        val ticks = cpuTicks(); val cpu = (ticks - lastTicks) / ticksPerSec / secs * 100
        lastFrames = player.framesRendered; lastNanos = player.renderNanos; lastTicks = ticks; lastT = now
        Ui.stats = "%.0f fps · %.1f ms/frame · CPU %.0f%% · %s · decode: %s".format(frames / secs, ms, cpu,
            "${player.targetWidth}x${player.targetHeight}", player.get("hwdec-current")?.ifBlank { null } ?: "software")
        println(Ui.stats); samples += Ui.stats
        val elapsed = (now - start) / 1e9
        if (snapshot != null && elapsed > 5 && !File(snapshot).exists()) Ui.frame?.let { saveFrame(it, snapshot) }
        if (seconds != null && elapsed >= seconds) { println("done"); exitProcess(0) }
    }
}

/** The current video frame as a PNG (the window itself can't be captured on Wayland). */
private fun saveFrame(bmp: Bitmap, path: String) {
    val img = org.jetbrains.skia.Image.makeFromBitmap(bmp)
    File(path).writeBytes(img.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
}
