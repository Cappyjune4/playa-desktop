package com.cappyjune.playa.desktop.data

import com.cappyjune.playa.desktop.playback.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * The videos on this computer: everything under the folders in Settings, grouped by the folder
 * each sits in. Length and size come from ffprobe; thumbnails from GNOME's own thumbnail cache
 * when Files has made one, else a frame from ffmpeg (both kept in Playa's cache).
 */
object LocalVideos {
    val EXTENSIONS = setOf("mkv", "mp4", "m4v", "avi", "mov", "webm", "wmv", "flv", "ts", "m2ts", "mts", "mpg", "mpeg", "ogv", "3gp", "vob", "divx", "rmvb")

    data class Video(val file: File, val size: Long, val modified: Long) {
        val title get() = file.nameWithoutExtension
        val folder get() = file.parentFile?.name ?: "/"
        val folderPath get() = file.parent ?: "/"
        val key get() = file.path
        fun toMedia() = Media(file.path, title, folder)
    }
    data class Folder(val path: String, val name: String, val videos: List<Video>) {
        val newest get() = videos.maxOf { it.modified }
    }

    @Serializable data class Meta(val modified: Long, val duration: Float = 0f, val width: Int = 0, val height: Int = 0)

    /** Every video under [roots], newest first; hidden folders are skipped. */
    suspend fun scan(roots: List<String>): List<Video> = withContext(Dispatchers.IO) {
        roots.map(::File).filter { it.isDirectory }.flatMap { root ->
            root.walkTopDown().onEnter { it == root || !it.name.startsWith(".") }.maxDepth(8)
                .filter { it.isFile && it.extension.lowercase() in EXTENSIONS && !it.name.startsWith(".") }
                .map { Video(it, it.length(), it.lastModified()) }.toList()
        }.distinctBy { it.file.canonicalPath }.sortedByDescending { it.modified }
    }

    fun folders(videos: List<Video>): List<Folder> =
        videos.groupBy { it.folderPath }.map { (p, v) -> Folder(p, File(p).name.ifBlank { p }, v) }.sortedByDescending { it.newest }

    // --- length and resolution --------------------------------------------------------------------

    private val metaFile = File(Paths.cache, "local-meta.json")
    private val metaSer = MapSerializer(String.serializer(), Meta.serializer())
    private val meta = ConcurrentHashMap<String, Meta>(runCatching { Http.json.decodeFromString(metaSer, metaFile.readText()) }.getOrDefault(emptyMap()))
    private val probing = Semaphore(4)

    /** ffprobe's answer for a file, remembered until the file changes. */
    suspend fun meta(v: Video): Meta? {
        meta[v.key]?.takeIf { it.modified == v.modified }?.let { return it }
        return probing.withPermit {
            withContext(Dispatchers.IO) {
                runCatching {
                    val p = ProcessBuilder("ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=width,height:format=duration",
                        "-of", "default=noprint_wrappers=1", v.file.path).redirectErrorStream(true).start()
                    val out = p.inputStream.bufferedReader().readText(); p.waitFor(20, TimeUnit.SECONDS)
                    val kv = out.lines().mapNotNull { l -> l.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
                    Meta(v.modified, kv["duration"]?.toFloatOrNull() ?: 0f, kv["width"]?.toIntOrNull() ?: 0, kv["height"]?.toIntOrNull() ?: 0)
                }.getOrNull()?.also { meta[v.key] = it; saveMeta() }
            }
        }
    }

    private fun saveMeta() = runCatching { File(metaFile.path + ".tmp").apply { writeText(Http.json.encodeToString(metaSer, HashMap(meta))); renameTo(metaFile) } }

    // --- thumbnails ------------------------------------------------------------------------------

    private val thumbs = File(Paths.cache, "thumbs").apply { mkdirs() }
    private val thumbing = Semaphore(3)

    private fun md5(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** A picture for the video: GNOME's thumbnail if it's up to date, else a frame a tenth of the way in. */
    suspend fun thumbnail(v: Video): File? = withContext(Dispatchers.IO) {
        val uri = v.file.toURI().toASCIIString().replaceFirst("file:/", "file:///")
        val hash = md5(uri)
        listOf("x-large", "large", "normal").map { File(System.getProperty("user.home"), ".cache/thumbnails/$it/$hash.png") }
            .firstOrNull { it.isFile && it.lastModified() >= v.modified }?.let { return@withContext it }
        val out = File(thumbs, "$hash.jpg")
        if (out.isFile && out.lastModified() >= v.modified) return@withContext out
        val at = (meta(v)?.duration ?: 0f).let { if (it > 20) it * 0.1f else 1f }
        thumbing.withPermit {
            runCatching {
                val tmp = File(thumbs, "$hash.tmp.jpg")
                val p = ProcessBuilder("ffmpeg", "-v", "error", "-y", "-ss", "%.1f".format(java.util.Locale.ROOT, at), "-i", v.file.path,
                    "-frames:v", "1", "-vf", "scale=480:-2", "-q:v", "4", tmp.path).redirectErrorStream(true).start()
                p.inputStream.readAllBytes()
                if (p.waitFor(30, TimeUnit.SECONDS) && tmp.length() > 0) tmp.renameTo(out)
            }
        }
        out.takeIf { it.isFile }
    }
}
