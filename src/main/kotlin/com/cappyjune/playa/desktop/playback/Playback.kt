package com.cappyjune.playa.desktop.playback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cappyjune.playa.desktop.data.Paths
import com.cappyjune.playa.desktop.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.skia.Bitmap
import java.io.File

/** Something to play: a file, a stream, a Jellyfin item (stage 3 fills in [headers] and [key]). */
@Serializable
data class Media(
    val uri: String,
    val title: String,
    val subtitle: String? = null,
    val artwork: String? = null,
    /** HTTP headers for the stream (Jellyfin's token goes here, never in the URL). */
    val headers: Map<String, String> = emptyMap(),
    /** Where resume points are kept for it; the file path by default. */
    val key: String = uri,
    val live: Boolean = false,
    /** Extra subtitle files to load with it. */
    val subtitles: List<String> = emptyList(),
    /** Where to start; when null, a local file picks up from its resume point. */
    val startSeconds: Float? = null,
    /** For a Jellyfin item: what to tell the server as it plays. */
    val jf: JfRef? = null,
)

/** A Jellyfin item being played: ids for the server's reports, and its seek-bar thumbnails. */
@Serializable
data class JfRef(val itemId: String, val sourceId: String? = null, val sessionId: String? = null, val method: String = "DirectPlay",
    val seriesId: String? = null, val trick: Trick? = null)

/** Jellyfin's trickplay: thumbnails every [intervalMs], [tileWidth]×[tileHeight] to a sheet. */
@Serializable
data class Trick(val width: Int, val height: Int, val tileWidth: Int, val tileHeight: Int, val count: Int, val intervalMs: Int)

/** An audio or subtitle track as mpv lists it. */
data class Track(val id: Int, val type: String, val title: String?, val lang: String?, val codec: String?, val selected: Boolean, val external: Boolean) {
    val label: String get() = listOfNotNull(title?.ifBlank { null }, lang?.let { java.util.Locale.forLanguageTag(it).displayLanguage.ifBlank { it } }, codec?.uppercase())
        .distinct().joinToString(" · ").ifBlank { "Track $id" }
}

enum class Scale(val label: String) { FIT("Fit"), FILL("Fill"), STRETCH("Stretch") }

/**
 * The one player: mpv, the queue, and everything the screen shows, as Compose state. Nothing
 * plays until [play]; [stop] closes the player screen and saves where you got to.
 */
object Playback {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mpv: MpvPlayer? = null

    // --- state for the screen ----------------------------------------------------------------
    var showing by mutableStateOf(false); private set
    /** The mini player (PiP) window is up instead of the full screen player. */
    var mini by mutableStateOf(false)
    var frame by mutableStateOf<Bitmap?>(null); private set
    var frameNo by mutableIntStateOf(0); private set
    var queue by mutableStateOf<List<Media>>(emptyList()); private set
    var index by mutableIntStateOf(0); private set
    val current: Media? get() = queue.getOrNull(index)
    var position by mutableFloatStateOf(0f); private set
    var duration by mutableFloatStateOf(0f); private set
    var buffered by mutableFloatStateOf(0f); private set
    var paused by mutableStateOf(false); private set
    var buffering by mutableStateOf(false); private set
    var ended by mutableStateOf(false); private set
    var volume by mutableFloatStateOf(100f); private set
    var muted by mutableStateOf(false); private set
    var speed by mutableFloatStateOf(1f); private set
    var scale by mutableStateOf(Scale.FIT); private set
    var tracks by mutableStateOf<List<Track>>(emptyList()); private set
    var videoWidth by mutableIntStateOf(0); private set
    var videoHeight by mutableIntStateOf(0); private set
    var error by mutableStateOf<String?>(null); private set
    /** Jellyfin's intro/credits markers for what's playing, for the Skip buttons. */
    var segments by mutableStateOf<List<com.cappyjune.playa.desktop.data.JfSegment>>(emptyList()); private set
    /** Goes up each time the player closes, so pages can refresh (Continue watching has moved on). */
    var closed by mutableIntStateOf(0); private set
    val nextTitle: String? get() = queue.getOrNull(index + 1)?.title
    /** The Jellyfin item whose playback the server has been told about. */
    private var reporting: JfRef? = null

    private fun player(): MpvPlayer = mpv ?: MpvPlayer().also { p ->
        mpv = p
        p.onFrame = { bmp -> frame = bmp; frameNo++ }
        p.set("volume-max", "150")
        poll(p)
    }

    /** Where mpv should draw: the player area's size in pixels (it scales the video to fit inside). */
    fun surfaceSize(w: Int, h: Int) {
        val p = mpv ?: return
        val vw = videoWidth.takeIf { it > 0 } ?: 16; val vh = videoHeight.takeIf { it > 0 } ?: 9
        // Fit: the video's own shape, as large as the area allows (never beyond its size);
        // fill and stretch: the whole area (mpv crops or stretches into it).
        if (scale == Scale.FIT) {
            val s = minOf(w.toFloat() / vw, h.toFloat() / vh, if (videoWidth > 0) 1f else Float.MAX_VALUE)
            p.targetWidth = (vw * s).toInt().coerceIn(16, w.coerceAtLeast(16)); p.targetHeight = (vh * s).toInt().coerceIn(16, h.coerceAtLeast(16))
        } else { p.targetWidth = w; p.targetHeight = h }
    }

    // --- controls -----------------------------------------------------------------------------

    fun play(items: List<Media>, start: Int = 0) {
        if (items.isEmpty()) return
        savePosition()
        queue = items; index = start.coerceIn(items.indices)
        showing = true
        load(items[index])
    }

    private fun load(m: Media) {
        reportStopped()
        error = null; ended = false; position = 0f; duration = 0f; tracks = emptyList(); segments = emptyList()
        // A queued Jellyfin episode: ask the server how to play it now its turn has come.
        if (m.uri.startsWith("jellyfin:")) {
            val at = index
            scope.launch {
                runCatching { com.cappyjune.playa.desktop.data.JellyfinPlay.resolve(m.uri.removePrefix("jellyfin:"), null) }
                    .onSuccess { r -> if (index == at && showing) { queue = queue.toMutableList().also { it[at] = r }; load(r) } }
                    .onFailure { error = "Couldn't play this: ${it.message}" }
            }
            return
        }
        val p = player()
        // Headers first (a Jellyfin stream needs its token), then where to start.
        // (A playlist's own user agent goes in mpv's option for it: as a header field it'd be sent twice.)
        p.set("user-agent", m.headers["User-Agent"] ?: "libmpv")
        p.set("http-header-fields", m.headers.filterKeys { it != "User-Agent" }.entries.joinToString(",") { (k, v) -> "$k: ${v.replace(",", "\\,")}" })
        val resume = if (m.live) null else m.startSeconds?.takeIf { it > 0 } ?: if (m.jf == null || m.key.startsWith("offline:")) Resume.at(m.key) else null
        p.set("start", resume?.let { String.format(java.util.Locale.ROOT, "+%.1f", it) } ?: "none")
        p.command("loadfile", m.uri, "replace")
        m.subtitles.forEach { p.command("sub-add", it, "auto") }
        p.set("pause", "no")
        m.jf?.let { j ->
            reporting = j
            scope.launch {
                com.cappyjune.playa.desktop.data.JellyfinPlay.started(j, resume ?: 0f)
                segments = com.cappyjune.playa.desktop.data.JfApi.segments(j.itemId)
            }
        }
    }

    /** Tells Jellyfin the last item stopped where it did (at its end, if it finished: that marks it watched). */
    private fun reportStopped() {
        val j = reporting ?: return
        reporting = null
        val at = if (ended && duration > 0) duration else position
        scope.launch { com.cappyjune.playa.desktop.data.JellyfinPlay.stopped(j, at) }
    }

    fun togglePause() { mpv?.let { if (ended) { seekTo(0f); it.set("pause", "no") } else it.command("cycle", "pause") } }
    fun seekTo(seconds: Float) { mpv?.command("seek", String.format(java.util.Locale.ROOT, "%.2f", seconds.coerceAtLeast(0f)), "absolute"); position = seconds }
    fun skip(seconds: Int) { mpv?.command("seek", seconds.toString(), "relative") }
    fun skipBack() = skip(-Settings.now.skipBack)
    fun skipForward() = skip(Settings.now.skipForward)
    fun changeVolume(v: Float) { mpv?.set("volume", String.format(java.util.Locale.ROOT, "%.0f", v.coerceIn(0f, 150f))); volume = v.coerceIn(0f, 150f); if (muted) toggleMute() }
    fun toggleMute() { mpv?.command("cycle", "mute") }
    fun changeSpeed(s: Float) { mpv?.set("speed", s.toString()); speed = s }
    fun changeScale(s: Scale) {
        scale = s
        mpv?.let { p ->
            // Fill: crop to the area (panscan); stretch: ignore the video's shape.
            p.set("panscan", if (s == Scale.FILL) "1.0" else "0.0")
            p.set("keepaspect", if (s == Scale.STRETCH) "no" else "yes")
        }
    }
    fun selectTrack(t: Track?, type: String) { mpv?.set(if (type == "audio") "aid" else "sid", t?.id?.toString() ?: "no") }
    fun addSubtitle(path: String) { mpv?.command("sub-add", path, "select") }
    val hasNext get() = index < queue.size - 1
    val hasPrevious get() = index > 0
    fun next() { if (hasNext) { savePosition(); index++; load(queue[index]) } }
    fun previous() {
        // Past the first few seconds, "previous" restarts; live TV always changes channel.
        if (!(current?.live ?: false) && (position > 5 || !hasPrevious)) seekTo(0f)
        else if (hasPrevious) { savePosition(); index--; load(queue[index]) }
    }

    fun stop() {
        savePosition()
        reportStopped()
        mpv?.command("stop")
        showing = false; mini = false; frame = null; queue = emptyList(); segments = emptyList()
        closed++
    }

    // --- following mpv -------------------------------------------------------------------------

    private var lastPaused = false

    private fun poll(p: MpvPlayer) = scope.launch {
        var tick = 0
        while (true) {
            delay(200)
            if (!showing) continue
            position = p.get("time-pos")?.toFloatOrNull() ?: position
            duration = p.get("duration")?.toFloatOrNull() ?: duration
            buffered = p.get("demuxer-cache-time")?.toFloatOrNull() ?: 0f
            paused = p.get("pause") == "yes"
            buffering = p.get("paused-for-cache") == "yes" || (p.get("core-idle") == "yes" && !paused && position == 0f)
            volume = p.get("volume")?.toFloatOrNull() ?: volume
            muted = p.get("mute") == "yes"
            speed = p.get("speed")?.toFloatOrNull() ?: speed
            videoWidth = p.get("dwidth")?.toIntOrNull() ?: 0; videoHeight = p.get("dheight")?.toIntOrNull() ?: 0
            val eof = p.get("eof-reached") == "yes"
            if (eof && !ended) {
                ended = true; Resume.finished(current?.key)
                current?.key?.takeIf { it.startsWith("offline:") }?.let { com.cappyjune.playa.desktop.data.Downloads.markWatched(it.removePrefix("offline:")) }
                if (hasNext) next() else reportStopped()
            }
            if (tick % 5 == 0) { tracks = readTracks(p); if (!paused) savePosition() }
            // Jellyfin hears how it's going every 10 seconds, and straight away on pause or play.
            val wasPaused = lastPaused; lastPaused = paused
            reporting?.let { j -> if (tick % 50 == 0 || wasPaused != paused) scope.launch { com.cappyjune.playa.desktop.data.JellyfinPlay.progress(j, position, paused) } }
            // Nothing loaded after a file was asked for: mpv couldn't open it.
            if (tick % 5 == 0 && p.get("idle-active") == "yes" && current != null && !ended && duration == 0f && tick > 10)
                error = "Can't play this file"
            tick++
        }
    }

    private fun readTracks(p: MpvPlayer): List<Track> {
        val n = p.get("track-list/count")?.toIntOrNull() ?: return emptyList()
        return (0 until n).mapNotNull { i ->
            val type = p.get("track-list/$i/type") ?: return@mapNotNull null
            Track(p.get("track-list/$i/id")?.toIntOrNull() ?: return@mapNotNull null, type, p.get("track-list/$i/title"), p.get("track-list/$i/lang"),
                p.get("track-list/$i/codec"), p.get("track-list/$i/selected") == "yes", p.get("track-list/$i/external") == "yes")
        }
    }

    private fun savePosition() {
        val m = current ?: return
        // Jellyfin streams keep their place on the server (see the reports); files keep it here,
        // downloads too (they report to the server as well, when it's reachable).
        if (!m.live && (m.jf == null || m.key.startsWith("offline:")) && duration > 0) Resume.save(m.key, position, duration)
    }
}

/** Where each video was left, so it picks up there next time (local files; Jellyfin keeps its own). */
object Resume {
    @Serializable data class Point(val position: Float, val duration: Float, val at: Long = System.currentTimeMillis())
    private val file = File(Paths.config, "resume.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val ser = MapSerializer(String.serializer(), Point.serializer())
    private val points: MutableMap<String, Point> = runCatching { json.decodeFromString(ser, file.readText()).toMutableMap() }.getOrNull() ?: mutableMapOf()

    /** Where to start: only after the first half-minute and before the last 5%. */
    fun at(key: String): Float? = points[key]?.takeIf { it.position > 30 && it.position < it.duration * 0.95 }?.position?.minus(3)
    fun save(key: String, position: Float, duration: Float) { points[key] = Point(position, duration); write() }
    fun finished(key: String?) { key ?: return; points.remove(key); write() }
    val all: Map<String, Point> get() = points
    private fun write() = runCatching { Paths.writePrivate(file, json.encodeToString(ser, points)) }
}
