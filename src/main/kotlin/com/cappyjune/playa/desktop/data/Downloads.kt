package com.cappyjune.playa.desktop.data

import com.cappyjune.playa.desktop.playback.JfRef
import com.cappyjune.playa.desktop.playback.Media
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.Request
import java.io.File

/**
 * Jellyfin films and episodes kept on this computer for watching offline (a laptop on a train):
 * the video, its subtitle files and artwork, so Offline looks right with no connection at all.
 * One at a time while Playa is open; a download cut off by closing it carries on next time.
 */
object Downloads {
    /** Original is the server's file untouched; the others are made smaller by the server. */
    enum class Quality(val label: String, val height: Int, val videoKbps: Int) {
        ORIGINAL("Original", 0, 0), P1080("1080p", 1080, 8000), P720("720p", 720, 4000), P480("480p", 480, 1500);
        /** Roughly how big it'll be, for the picker. */
        fun estimate(runtimeMs: Long, originalBytes: Long?): Long? =
            if (this == ORIGINAL) originalBytes else runtimeMs / 1000 * (videoKbps + 192) * 1000 / 8
    }

    @Serializable enum class State { QUEUED, DOWNLOADING, DONE, FAILED }

    @Serializable
    data class Item(
        val id: String, val type: String, val title: String, val seriesId: String? = null, val seriesName: String? = null,
        val season: Int? = null, val episode: Int? = null, val year: Int? = null, val overview: String? = null,
        val runtimeMs: Long = 0, val quality: Quality = Quality.ORIGINAL, val sourceId: String? = null,
        val state: State = State.QUEUED, val bytes: Long = 0, val total: Long = 0, val error: String? = null,
        val file: String? = null, val subs: List<String> = emptyList(), val poster: String? = null, val still: String? = null,
        val addedAt: Long = System.currentTimeMillis(),
    ) {
        val done get() = state == State.DONE && file != null && File(file).exists()
        val percent get() = if (total > 0) (bytes * 100 / total).toInt() else 0
        val code get() = listOfNotNull(season?.let { "S$it" }, episode?.let { "E$it" }).joinToString(" · ").ifBlank { null }
        val key get() = "offline:$id"
        fun toMedia() = Media(file!!, listOfNotNull(code, title).joinToString("  "), seriesName ?: year?.toString(), still ?: poster,
            key = key, subtitles = subs, jf = JfRef(id, sourceId, method = "DirectPlay", seriesId = seriesId))
    }

    val dir: File = File(Paths.data, "Jellyfin").apply { mkdirs() }
    private val ser = MapSerializer(String.serializer(), Item.serializer())
    private val _items = MutableStateFlow(
        runCatching { Http.json.decodeFromString(ser, File(dir, "downloads.json").readText()) }.getOrDefault(emptyMap())
            .mapValues { (_, v) -> if (v.state == State.DOWNLOADING) v.copy(state = State.QUEUED) else v })
    val items: StateFlow<Map<String, Item>> = _items.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var worker: Job? = null

    /** Picks up anything left in the queue last time (once signed in). */
    fun start() { if (_items.value.values.any { it.state == State.QUEUED }) kick() }

    fun enqueue(items: List<JfItem>, quality: Quality) {
        val playable = items.filter { it.playable && _items.value[it.Id]?.done != true }
        if (playable.isEmpty()) return
        _items.update { m -> m + playable.associate { i ->
            i.Id to Item(i.Id, i.Type.orEmpty(), i.Name.orEmpty(), i.SeriesId, i.SeriesName, i.ParentIndexNumber, i.IndexNumber,
                i.ProductionYear, i.Overview, i.runtimeMs, quality, i.MediaSources?.firstOrNull()?.Id,
                // A smaller copy's size isn't known until it's made, so progress runs against the estimate.
                total = quality.estimate(i.runtimeMs, i.MediaSources?.firstOrNull()?.Size) ?: 0)
        } }
        save(); kick()
    }

    fun retry(id: String) { update(id) { it.copy(state = State.QUEUED, error = null) }; save(); kick() }

    /** Everything for an item is in its own folder, a half-done .part too (a running download sees it gone and stops). */
    fun delete(ids: Collection<String>) {
        _items.update { it - ids.toSet() }
        ids.forEach { File(dir, it).deleteRecursively() }
        save()
    }

    fun deleteWatched() = delete(_items.value.values.filter { it.done && it.id in watched }.map { it.id })

    /** Downloads played to the end here (kept apart from Resume, which forgets finished things). */
    private val watchedFile = File(dir, "watched.json")
    private val watched: MutableSet<String> = runCatching { Http.json.decodeFromString<Set<String>>(watchedFile.readText()).toMutableSet() }.getOrDefault(mutableSetOf())
    fun markWatched(id: String) { if (watched.add(id)) runCatching { watchedFile.writeText(Http.json.encodeToString(watched)) } }
    fun isWatched(id: String) = id in watched

    fun bytesUsed(): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun update(id: String, f: (Item) -> Item) = _items.update { m -> m[id]?.let { m + (id to f(it)) } ?: m }
    private fun save() = runCatching { File(dir, "downloads.json.tmp").apply { writeText(Http.json.encodeToString(ser, _items.value)); renameTo(File(dir, "downloads.json")) } }

    @Synchronized private fun kick() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            while (true) {
                val acct = Jellyfin.account.value ?: break
                val d = _items.value.values.filter { it.state == State.QUEUED }.minByOrNull { it.addedAt } ?: break
                update(d.id) { it.copy(state = State.DOWNLOADING, error = null) }
                runCatching { fetch(acct, d) }.onFailure { e ->
                    if (d.id !in _items.value) { File(dir, d.id).deleteRecursively(); return@onFailure }
                    update(d.id) { it.copy(state = State.FAILED, error = e.message) }
                }
                save()
            }
        }
    }

    /** Which URL to fetch: the server's own file, or a smaller copy it makes as we download. */
    private fun url(a: JellyfinAccount, d: Item): String = when (d.quality) {
        Quality.ORIGINAL -> "${a.server}/Items/${d.id}/Download"
        // MPEG-TS: a transcode written as it goes can't go back to add a seek index; mpv seeks TS fine.
        else -> "${a.server}/Videos/${d.id}/stream.ts?static=false" + (d.sourceId?.let { "&MediaSourceId=$it" } ?: "") +
            "&VideoCodec=h264&AudioCodec=aac&MaxAudioChannels=2&AudioBitrate=192000&VideoBitrate=${d.quality.videoKbps * 1000}" +
            "&MaxHeight=${d.quality.height}&DeviceId=${Settings.now.deviceId}&SubtitleMethod=Drop&Context=Static"
    }

    private fun get(url: String, from: Long = 0) = Http.client.newCall(
        Request.Builder().url(url).apply { if (from > 0) header("Range", "bytes=$from-") }.build()).execute()

    private suspend fun fetch(acct: JellyfinAccount, d: Item) {
        val folder = File(dir, d.id).apply { mkdirs() }
        // Artwork and subtitle files first: small, and Offline needs them.
        val item = runCatching { JfApi.item(d.id) }.getOrNull()
        val poster = item?.let { JfApi.poster(it, 500) }?.let { saveTo(it, File(folder, "poster.jpg")) }
        val still = item?.let { JfApi.still(it, 800) }?.let { saveTo(it, File(folder, "still.jpg")) }
        val src = item?.MediaSources?.firstOrNull()
        val textSubs = src?.MediaStreams.orEmpty().filter { it.Type == "Subtitle" && it.IsTextSubtitleStream && (it.IsExternal || d.quality != Quality.ORIGINAL) }
        val subs = textSubs.mapNotNull { s ->
            val ext = if (s.Codec?.lowercase() in setOf("ass", "ssa") && d.quality == Quality.ORIGINAL) "ass" else "srt"
            // Named with the language, so mpv's track list says what each is.
            val f = File(folder, "sub-${s.Index}${s.Language?.let { ".$it" } ?: ""}.$ext")
            saveTo("${acct.server}/Videos/${d.id}/${src!!.Id}/Subtitles/${s.Index}/Stream.$ext", f)
        }
        update(d.id) { it.copy(poster = poster, still = still, subs = subs, overview = item?.Overview ?: it.overview) }

        val ext = if (d.quality == Quality.ORIGINAL) (src?.Container?.substringBefore(',')?.ifBlank { null } ?: "mkv") else "ts"
        val out = File(folder, "video.$ext")
        val part = File(folder, "video.$ext.part")
        // Pick up where a cut-off original download stopped; a transcode has to start again.
        val resume = if (d.quality == Quality.ORIGINAL && part.exists()) part.length() else { part.delete(); 0L }
        get(url(acct, d), resume).use { r ->
            if (!r.isSuccessful) error(if (r.code == 403) "This account isn't allowed to download" else "Server said ${r.code}")
            val appending = resume > 0 && r.code == 206
            val len = r.body.contentLength().let { if (it > 0) it + (if (appending) resume else 0) else d.total }
            var got = if (appending) resume else 0L
            var lastUpdate = 0L
            r.body.byteStream().use { input ->
                java.io.FileOutputStream(part, appending).use { o ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        if (d.id !in _items.value) error("cancelled")
                        val n = input.read(buf); if (n < 0) break
                        o.write(buf, 0, n); got += n
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 500) { lastUpdate = now; update(d.id) { it.copy(bytes = got, total = len) } }
                    }
                }
            }
        }
        if (d.id !in _items.value) error("cancelled")
        if (!part.renameTo(out)) error("Couldn't save the file")
        update(d.id) { it.copy(state = State.DONE, file = out.absolutePath, bytes = out.length(), total = out.length()) }
    }

    private fun saveTo(url: String, f: File): String? = runCatching {
        get(url).use { r ->
            if (!r.isSuccessful) return@use null
            r.body.byteStream().use { i -> f.outputStream().use { i.copyTo(it) } }
            f.absolutePath
        }
    }.getOrNull()
}
