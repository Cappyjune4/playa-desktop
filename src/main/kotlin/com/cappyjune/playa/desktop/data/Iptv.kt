package com.cappyjune.playa.desktop.data

import com.cappyjune.playa.desktop.playback.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/**
 * IPTV playlists, as on the phone: the muOS IPTVViewer's lists (four bundled, four online),
 * Hypnotix's default Free-TV, and any M3U added here. Online lists are cached for a day.
 */
object Iptv {
    @Serializable
    data class Playlist(val id: String, val name: String, val url: String? = null, val asset: String? = null, val custom: Boolean = false)

    @Serializable
    data class Channel(
        val name: String, val url: String, val group: String = "", val logo: String = "", val tvgId: String = "",
        val userAgent: String? = null, val referrer: String? = null,
    ) {
        /** Hypnotix's split: VOD groups are films, SERIES groups are series, the rest is TV. */
        val section get() = group.uppercase().split(' ', '|', '-', '/').let { w -> when { "VOD" in w -> Section.MOVIES; "SERIES" in w -> Section.SERIES; else -> Section.TV } }
        fun toMedia(playlist: String) = Media(
            url, name, group.ifBlank { playlist }, logo.ifBlank { null }, key = url, live = section == Section.TV,
            headers = buildMap { userAgent?.let { put("User-Agent", it) }; referrer?.let { put("Referer", it) } },
        )
    }
    enum class Section(val label: String) { TV("TV"), MOVIES("Movies"), SERIES("Series") }

    val builtIn = listOf(
        Playlist("freetv", "Free-TV", url = "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8"),
        Playlist("iptvorg-country", "By country", url = "https://iptv-org.github.io/iptv/index.country.m3u"),
        Playlist("iptvorg-language", "By language", url = "https://iptv-org.github.io/iptv/index.language.m3u"),
        Playlist("iptvorg-all", "iptv-org (all)", url = "https://iptv-org.github.io/iptv/index.m3u"),
        Playlist("iptv-2025", "IPTV 2025", asset = "iptv/iptv-2025.m3u"),
        Playlist("big-list", "Big list", asset = "iptv/big-list.m3u"),
        Playlist("news", "News", asset = "iptv/news.m3u"),
        Playlist("auto", "Auto", asset = "iptv/auto.m3u"),
        Playlist("iptv-ru", "IPTV RUS", url = "https://smolnp.github.io/IPTVru//IPTVru.m3u"),
    )

    private val dir = File(Paths.config, "iptv").apply { mkdirs() }
    private val cacheDir = File(Paths.cache, "iptv").apply { mkdirs() }
    private val _custom = MutableStateFlow(read("playlists.json", ListSerializer(Playlist.serializer())) ?: emptyList())
    val custom: StateFlow<List<Playlist>> = _custom.asStateFlow()
    val playlists get() = builtIn + _custom.value
    private val _favourites = MutableStateFlow(read("favourites.json", ListSerializer(Channel.serializer())) ?: emptyList())
    val favourites: StateFlow<List<Channel>> = _favourites.asStateFlow()
    private val _recent = MutableStateFlow(read("recent.json", ListSerializer(Channel.serializer())) ?: emptyList())
    val recent: StateFlow<List<Channel>> = _recent.asStateFlow()
    private val parsed = HashMap<String, List<Channel>>()

    /** A playlist's channels: from memory, the cache, the app, or the network, in that order. */
    suspend fun channels(p: Playlist, refresh: Boolean = false): List<Channel> = withContext(Dispatchers.IO) {
        if (!refresh) synchronized(parsed) { parsed[p.id] }?.let { return@withContext it }
        val text = when {
            p.asset != null -> Iptv::class.java.classLoader.getResourceAsStream(p.asset)!!.bufferedReader().use { it.readText() }
            else -> {
                val cache = File(cacheDir, "${p.id}.m3u")
                val fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < 24 * 3600_000
                if (!refresh && fresh) cache.readText()
                else runCatching { Http.get(p.url!!).also { body -> File(cache.path + ".part").apply { writeText(body); renameTo(cache) } } }
                    .getOrElse { e -> if (cache.exists()) cache.readText() else throw e }
            }
        }
        parse(text).also { synchronized(parsed) { parsed[p.id] = it } }
    }

    /** #EXTINF attributes, the name after the comma, and VLC's per-stream user-agent/referrer options. */
    fun parse(text: String): List<Channel> {
        val out = ArrayList<Channel>(4096)
        val attr = Regex("""([A-Za-z0-9-]+)="([^"]*)"""")
        var info: String? = null
        var ua: String? = null; var ref: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> {}
                line.startsWith("#EXTINF", ignoreCase = true) -> { info = line; ua = null; ref = null }
                line.startsWith("#EXTVLCOPT:http-user-agent=", ignoreCase = true) -> ua = line.substringAfter('=')
                line.startsWith("#EXTVLCOPT:http-referrer=", ignoreCase = true) -> ref = line.substringAfter('=')
                line.startsWith("#") -> {}
                else -> {
                    val i = info ?: continue
                    // The name follows the comma after the last quoted attribute (values can hold commas).
                    val start = i.lastIndexOf("\",").let { q -> if (q >= 0) q + 2 else i.indexOf(',') + 1 }.coerceIn(0, i.length)
                    val attrs = attr.findAll(i.substring(0, start)).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    val name = i.substring(start).trim().ifBlank { attrs["tvg-name"].orEmpty() }.ifBlank { line.substringAfterLast('/') }
                    out += Channel(name, line, attrs["group-title"].orEmpty().trim(), attrs["tvg-logo"].orEmpty(), attrs["tvg-id"].orEmpty(),
                        ua ?: attrs["user-agent"] ?: attrs["http-user-agent"], ref ?: attrs["http-referrer"] ?: attrs["referrer"])
                    info = null
                }
            }
        }
        return out
    }

    fun groups(channels: List<Channel>, section: Section): List<Pair<String, List<Channel>>> =
        channels.filter { it.section == section }.groupBy { it.group.ifBlank { "Other" } }.toList()
            .sortedWith(compareBy({ it.first.equals("Undefined", true) || it.first == "Other" }, { label(it.first).lowercase() }))

    /** A group's name as Hypnotix shows it: without the VOD/SERIES marker words. */
    fun label(group: String) = group.split(' ').filterNot { it.equals("VOD", true) || it.equals("SERIES", true) }.joinToString(" ").trim().ifBlank { group }

    fun toggleFavourite(c: Channel) {
        _favourites.update { l -> if (l.any { it.url == c.url }) l.filterNot { it.url == c.url } else l + c }
        write("favourites.json", ListSerializer(Channel.serializer()), _favourites.value)
    }
    fun played(c: Channel) {
        _recent.update { l -> (listOf(c) + l.filterNot { it.url == c.url }).take(30) }
        write("recent.json", ListSerializer(Channel.serializer()), _recent.value)
    }

    fun addPlaylist(name: String, url: String) {
        val p = Playlist("custom-" + url.hashCode().toUInt().toString(16), name.ifBlank { url.substringAfterLast('/') }, url = url.trim(), custom = true)
        _custom.update { l -> l.filterNot { it.id == p.id } + p }
        write("playlists.json", ListSerializer(Playlist.serializer()), _custom.value)
    }
    fun removePlaylist(p: Playlist) {
        _custom.update { l -> l.filterNot { it.id == p.id } }
        synchronized(parsed) { parsed.remove(p.id) }; File(cacheDir, "${p.id}.m3u").delete()
        write("playlists.json", ListSerializer(Playlist.serializer()), _custom.value)
    }

    private fun <T> read(name: String, s: kotlinx.serialization.KSerializer<T>): T? =
        runCatching { Http.json.decodeFromString(s, File(dir, name).readText()) }.getOrNull()
    private fun <T> write(name: String, s: kotlinx.serialization.KSerializer<T>, v: T) {
        runCatching { File(dir, "$name.tmp").apply { writeText(Http.json.encodeToString(s, v)); renameTo(File(dir, name)) } }
    }
}
